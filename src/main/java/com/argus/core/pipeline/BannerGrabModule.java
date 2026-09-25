package com.argus.core.pipeline;

import com.argus.core.model.PortResult;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 4 — banner-grab (CORE.md): web ports get a real HTTP GET (TLS on 443
 * and 8443) and the title plus Server header are recorded; raw ports get the
 * first bytes the server sends, which is how SSH, FTP and SMTP identify
 * themselves on connect. Any other open port passes through untouched. The
 * host name is resolved through the injected resolver, so tests stay offline.
 */
public final class BannerGrabModule implements ArgusModule {

    private static final int CONNECT_TIMEOUT_MS = 1500;
    private static final int READ_TIMEOUT_MS = 1500;
    private static final int MAX_TITLE = 200;
    private static final int MAX_RESPONSE = 64 * 1024;
    private static final Set<Integer> WEB_PORTS = Set.of(80, 443, 8080, 8443);
    private static final Set<Integer> RAW_PORTS = Set.of(22, 21, 25, 110);
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    private final BlockingQueue<Object> out;
    private final CancellationToken token;
    private final Function<String, Optional<String>> resolver;
    private final Set<Integer> webPorts;
    private final Set<Integer> rawPorts;
    private final Pump pump;

    public BannerGrabModule(BlockingQueue<Object> in, BlockingQueue<Object> out,
                            CancellationToken token, int workers,
                            Function<String, Optional<String>> resolver) {
        this(in, out, token, workers, resolver, WEB_PORTS, RAW_PORTS);
    }

    /** Test seam: ephemeral loopback ports stand in for the spec's fixed sets. */
    BannerGrabModule(BlockingQueue<Object> in, BlockingQueue<Object> out,
                     CancellationToken token, int workers,
                     Function<String, Optional<String>> resolver,
                     Set<Integer> webPorts, Set<Integer> rawPorts) {
        this.out = out;
        this.token = token;
        this.resolver = resolver;
        this.webPorts = webPorts;
        this.rawPorts = rawPorts;
        this.pump = new Pump(in, out, token, workers);
    }

    @Override
    public ModuleDescriptor descriptor() {
        return new ModuleDescriptor("banner-grab", ScanPhase.RECON, RiskLevel.ACTIVE);
    }

    @Override
    public boolean supports(TargetContext ctx) {
        return ctx != null;
    }

    @Override
    public void execute(TargetContext ctx) {
        pump.run(item -> {
            if (item instanceof PortResult p) {
                out.add(grab(ctx, p));
            } else {
                out.add(item); // Host rows pass through untouched
            }
        });
    }

    private PortResult grab(TargetContext ctx, PortResult p) {
        if (token.isCancelled() || !ctx.inScope(p.host())) {
            return p;
        }
        Optional<String> ip = resolver.apply(p.host());
        if (ip.isEmpty()) {
            return p;
        }
        try {
            if (webPorts.contains(p.port())) {
                return http(p, ip.get());
            }
            if (rawPorts.contains(p.port())) {
                return raw(p, ip.get());
            }
        } catch (IOException e) {
            return p; // closed mid-flight, refused, or unreadable — keep bare row
        }
        return p;
    }

    // --- web ports -------------------------------------------------------

    private PortResult http(PortResult p, String ip) throws IOException {
        boolean tls = p.port() == 443 || p.port() == 8443;
        if (tls) {
            try (SSLSocket socket = tlsSocket(ip, p.port())) {
                return exchange(socket, p, true);
            }
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, p.port()), CONNECT_TIMEOUT_MS);
            return exchange(socket, p, false);
        }
    }

    private SSLSocket tlsSocket(String ip, int port) throws IOException {
        SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
        socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS);
        socket.startHandshake();
        return socket;
    }

    private PortResult exchange(Socket socket, PortResult p, boolean tls) throws IOException {
        socket.setSoTimeout(READ_TIMEOUT_MS);
        String authority = p.port() == 80 || p.port() == 443
                ? p.host() : p.host() + ":" + p.port();
        String request = "GET / HTTP/1.1\r\nHost: " + authority
                + "\r\nConnection: close\r\nUser-Agent: Argus\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(request.getBytes(StandardCharsets.US_ASCII));
        out.flush();

        byte[] response = readUntilQuiet(socket);
        if (response.length == 0) {
            return p;
        }
        String text = new String(response, StandardCharsets.ISO_8859_1);
        int split = text.indexOf("\r\n\r\n");
        String head = split >= 0 ? text.substring(0, split) : text;
        String status = head.lines().findFirst().orElse("");
        String server = header(head, "Server");
        String banner = server != null ? "Server: " + server : status;
        String service = tls ? "https" : "http";
        return new PortResult(p.host(), p.port(), p.protocol(), service,
                p.version(), banner, title(text), p.open());
    }

    /** Connection: close ends the read; a slow server ends it at SO_TIMEOUT. */
    private static byte[] readUntilQuiet(Socket socket) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        try {
            InputStream in = socket.getInputStream();
            int n;
            while ((n = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, n);
                if (buffer.size() >= MAX_RESPONSE) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            // partial response is still useful — fall through and parse it
        }
        return buffer.toByteArray();
    }

    private static String header(String head, String name) {
        for (String line : head.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.regionMatches(true, 0, name, 0, colon)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static String title(String text) {
        Matcher matcher = TITLE.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        String found = matcher.group(1).replaceAll("\\s+", " ").trim();
        return found.length() > MAX_TITLE ? found.substring(0, MAX_TITLE) : found;
    }

    // --- raw ports -------------------------------------------------------

    private PortResult raw(PortResult p, String ip) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, p.port()), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            String banner = firstLine(socket);
            if (banner.isEmpty()) {
                return p;
            }
            return new PortResult(p.host(), p.port(), p.protocol(), serviceOf(p.port()),
                    p.version(), banner, p.title(), p.open());
        }
    }

    /** Servers that identify on connect send their line first — just read it. */
    private static String firstLine(Socket socket) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            InputStream in = socket.getInputStream();
            int b;
            while (buffer.size() < 512 && (b = in.read()) != -1) {
                buffer.write(b);
                if (b == '\n') {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            // silence from the server: nothing to grab
        }
        return buffer.toString(StandardCharsets.ISO_8859_1).trim();
    }

    private static String serviceOf(int port) {
        return switch (port) {
            case 22 -> "ssh";
            case 21 -> "ftp";
            case 25 -> "smtp";
            case 110 -> "pop3";
            default -> "";
        };
    }
}

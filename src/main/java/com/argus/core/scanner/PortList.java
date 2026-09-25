package com.argus.core.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Profile → port list (CORE.md): quick = top 100, full = top 1000, custom =
 * user list, all in {@code resources/ports/}. Unknown or missing file falls
 * back to quick — a scan never dies over a list.
 */
public final class PortList {

    private PortList() {
    }

    public static List<Integer> forProfile(String profile) {
        String name = switch (profile == null ? "" : profile) {
            case "full" -> "full";
            case "custom" -> "custom";
            default -> "quick";
        };
        List<Integer> ports = read("/ports/" + name + ".txt");
        return ports.isEmpty() ? read("/ports/quick.txt") : ports;
    }

    /** Raw text of the user's custom list, or "" when it does not exist yet. */
    public static String customText() {
        try (InputStream in = PortList.class.getResourceAsStream("/ports/custom.txt")) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Persists the user list from the Targets dialog. ponytail: written next to
     * the bundled resources (code-source dir), so a jar-packaged build would
     * need ~/.argus/ports/custom.txt instead.
     */
    public static void saveCustom(List<Integer> ports) throws IOException {
        Path dir;
        try {
            dir = customPath().getParent();
        } catch (URISyntaxException e) {
            throw new IOException("cannot locate resource directory", e);
        }
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("custom.txt"),
                ports.stream().map(String::valueOf).collect(Collectors.joining("\n")) + "\n",
                StandardCharsets.UTF_8);
    }

    /** Where custom.txt lives — next to the bundled lists, under the code-source dir. */
    static Path customPath() throws URISyntaxException {
        return Path.of(Objects.requireNonNull(
                        PortList.class.getProtectionDomain().getCodeSource().getLocation().toURI()))
                .resolve("ports")
                .resolve("custom.txt");
    }

    private static List<Integer> read(String resource) {
        try (InputStream in = PortList.class.getResourceAsStream(resource)) {
            if (in == null) {
                return List.of();
            }
            List<Integer> ports = new ArrayList<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String s = line.trim();
                if (!s.isEmpty()) {
                    ports.add(Integer.parseInt(s));
                }
            }
            return ports;
        } catch (IOException | NumberFormatException e) {
            return List.of();
        }
    }
}

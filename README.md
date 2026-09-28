# Argus

Local-first reconnaissance and target-package generator for authorized security
assessment. Subdomain enumeration, port discovery, service fingerprinting,
known-exploitable-vulnerability matching, and a ranked entry-point list for
whoever owns the assessment.

Everything runs locally. The only outbound traffic goes to the data providers
listed below. No service, no account, no telemetry.

![Sign in](docs/images/sign-in.png)
![Dashboard](docs/images/dashboard.png)

## What it does

1. **Enumerate subdomains** for a target root domain through certificate
   transparency logs.
2. **Resolve** the names and keep what points at a routable address.
3. **Probe ports** from a ranked list, so likely-open ports are tried first.
4. **Fingerprint services** from the banner and page title.
5. **Match banners against CISA KEV** to flag software with a known exploited
   vulnerability.
6. **Enrich live hosts** with geolocation, ASN and network owner, then check
   reputation against VirusTotal.
7. **Rank entry points** by a published priority score.
8. **Export a target package** as Markdown or JSON.

Discovery only. Nothing here attempts exploitation, brute force or evasion.

## Screenshots

| | |
|---|---|
| ![Results with host inspector](docs/images/results.png) | ![Entry points with findings](docs/images/entry-points.png) |
| Results, with the selected host's network detail and open ports | Entry points, with the selected port's findings |
| ![Targets](docs/images/targets.png) | ![API keys](docs/images/api-keys.png) |
| Targets, with scope and profile | Provider key storage |

## Requirements

- JDK 21 or newer
- Maven 3.9 or newer
- Outbound access to the providers below
- A display

## Build and run

```bash
mvn clean package
mvn javafx:run
```

The first start creates an operator account. The password is never stored, and
the vault key is derived from it, so it cannot be recovered once lost.

## How a scan runs

Six stages, each with its own worker pool and a cancellation token. A stage
drains before the next one starts.

```
crt.sh  ->  DNS resolve  ->  port scan  ->  banner grab  ->  KEV match  ->  collect
                                                                            |
                                          ip-api enrichment <--------------+
                                                    |
                              VirusTotal reputation on the top 10 IPs <---+
```

- Subdomain enumeration reads certificate transparency records for the root
  domain.
- Port scanning uses virtual threads, one per probe.
- A run stops after one hour, whichever stage it is in.
- Cancelling, or closing the window mid scan, keeps the results collected so far
  and marks the run `CANCELLED`. A run still marked `RUNNING` at the next start
  is marked `FAILED` during startup rather than left dangling.
- Enrichment runs after the pipeline, because both providers need the final host
  set. A provider failure degrades that provider's results and never fails the
  scan.

### Port profiles

| Profile | Ports |
|---|---|
| `quick` | 100, frequency ranked |
| `full` | 1000, frequency ranked |
| `custom` | supplied list, comma separated, 1 to 65535 |

The ranked lists come from a port frequency census, so common web, mail and
remote access ports are probed before the long tail. Saving a custom profile
replaces it for every later scan.

## Entry point ranking

Each open port is scored, capped at 100. The list shows the top 100.

| Signal | Points |
|---|---|
| KEV match, confirmed | 35 |
| KEV match, candidate | 15 |
| Known ransomware family in the CVE | 10 |
| VirusTotal flags it, 3 or more engines | 10 |
| Internet-facing web or admin port | 10 |

Ties break on `host:port`, so the same scan always produces the same list, and
the view and the export agree on it.

## Data providers

| Provider | Used for | Key | Quota behaviour |
|---|---|---|---|
| [crt.sh](https://crt.sh) | Subdomain enumeration | No | 24 hour response cache, one call per scan |
| [CISA KEV](https://www.cisa.gov/known-exploited-vulnerabilities-catalog) | Vulnerability matching | No | 7 day cache, bundled snapshot when the feed is unreachable |
| [ip-api.com](https://ip-api.com) | Country, ASN, network owner | No | 7 day per-IP cache, batched, 100 addresses per request |
| [VirusTotal](https://www.virustotal.com) | IP reputation | Yes | 24 hour cache, 4 requests per 15 seconds, top 10 ranked IPs only |

Provider keys are encrypted in the local database, never logged, and never
included in an export.

## Configuration

Keys are entered in the interface and stored encrypted. A key can also come from
the environment at run time, which is never written to disk:

```bash
export ARGUS_VT_KEY=...
mvn javafx:run
```

## Where the data lives

One SQLite file, by default `~/.argus/argus.db`. It holds operators, targets,
scans, hosts, ports, findings, encrypted provider keys, a provider response
cache, and an audit log of authentication and lock events. Deleting the file
resets the application. It contains the encrypted keys, so it is treated as
sensitive and worth backing up.

## Security notes

- Passwords are stored as a PBKDF2-HMAC-SHA256 verifier at 600,000 iterations,
  never in the clear and never logged.
- The vault key is derived from the password with a separate salt, held in
  memory for the session only, and wiped when the session ends.
- Provider keys are encrypted with AES-256-GCM using a fresh random IV per
  encryption.
- Failed logins are counted and written to the audit log. The fifth locks the
  account for 30 seconds, doubling per further failure up to 15 minutes.
- The session locks itself after five minutes of inactivity.
- A stored key is never rendered back into the interface.

## Authorized use

Argus enumerates and probes hosts, and is intended for infrastructure its
operator owns or has written permission to assess. Certificate transparency and
port probing reach infrastructure the operator does not control, so their terms
of service and applicable law apply.

## Project layout

```
src/main/java/com/argus/
  core/        pipeline stages, scanner, providers, crypto, concurrency, export
  db/          SQLite DAOs
  ui/          JavaFX controllers, view models, FXML and CSS
src/main/resources/
  com/argus/ui/view/   FXML views and the design token sheet
  kev/        bundled KEV snapshot, product aliases, version ranges
  ports/      ranked port lists
src/test/java/         the suite
docs/fixtures/         recorded provider responses, so tests never call out
```

## Tests

```bash
mvn verify
```

170 tests, no live network calls. Provider behaviour is tested against recorded
responses in `docs/fixtures` and a local HTTP server, so a run costs nothing and
needs no keys. A number of tests load the interface, which needs a display, so
the suite runs as a local gate.

## Limitations

- Subdomain enumeration inherits certificate transparency coverage. A host that
  never appeared in a certificate log is not found.
- Fingerprinting reports what a service advertises, not what it is.
- KEV matching is a version range comparison against the banner. A generic or
  mismatched banner produces no match rather than a wrong one, so false
  negatives are expected.
- Reputation is sampled: only the top ten ranked IPs are checked, to stay inside
  a free tier.
- Rankings and findings are inputs to an assessment, not conclusions.

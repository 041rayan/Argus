# Argus

Local-first red team reconnaissance and target-package generator. Discovery only.
Java 21, JavaFX, SQLite. Specs live beside this README.

## Run

```
mvn javafx:run
```

First start creates the operator account in the login scene.

## Test

```
mvn verify
```

## Development API key fallback

For development only, keys may come from environment variables instead of the
encrypted vault: `ARGUS_VT_KEY` (VirusTotal) and `ARGUS_SHODAN_KEY` (Shodan).
A convenience for hacking on the pipeline — never a substitute for the vault,
and never commit a value.

# Torrentino — BitTorrent Client (Java)

A simple, cross‑platform BitTorrent client written in Java with a unified workflow: it checks existing files, resumes where you left off, downloads any missing parts, and then keeps seeding.

### Key Features
- **Resume/Pause**: Stop the app anytime (Ctrl+C). When you start it again with the same `.torrent` and download folder, it **resumes** automatically.
- **Integrity verification**: Every piece is verified with the torrent's **SHA‑1 hash** to ensure files are intact. If a piece is corrupt, it is **re-downloaded automatically**. Note: this is integrity checking, not antivirus scanning.
- **Download and Upload**: Downloads missing pieces and **uploads (seeds) what you have** simultaneously once pieces are verified.
- **HTTP and UDP trackers**: Connects to both **HTTP** and **UDP** trackers, with periodic announcements to stay discoverable.
- **Multiple torrents at once**: Pass one or many `.torrent` files in a single run.
- **Configurable via CLI**: Choose download folder, listening port, connection limits, announce interval, and log verbosity.
- **Progress feedback**: Clear console updates during download and seeding.

### Requirements
- **Java 17+** (see `pom.xml` `maven.compiler.source/target = 17`)
- macOS, Linux, or Windows

## Download and Run
1. **Download** the latest release JAR from the repository’s Releases page.
2. Open a terminal in the folder containing the JAR and run:

```bash
java -jar torrentino-0.1.0-beta.jar <file.torrent>
```

Optional flags (examples below) let you change the download folder, port, connection limits, etc. Use `--help` anytime.

## Getting Started for Developers
Build from source with Maven:

```bash
git clone <this-repo-url>
cd bittorrent-client-java
mvn clean package

# Run the fat JAR
java -jar target/torrentino-0.1.0-beta-jar-with-dependencies.jar <file.torrent>
```

Requires **Java 17+** and **Maven**.

## Usage
Basic:

```bash
java -jar target/torrentino-0.1.0-beta-jar-with-dependencies.jar <file.torrent>
```

Multiple torrents:

```bash
java -jar target/torrentino-0.1.0-beta-jar-with-dependencies.jar torrent1.torrent torrent2.torrent
```

Custom download directory:

```bash
java -jar target/torrentino-0.1.0-beta-jar-with-dependencies.jar \
  --download-dir "/path/to/Downloads/BitTorrent" \
  <file.torrent>
```

Tune port and connection limits:

```bash
java -jar target/torrentino-0.1.0-beta-jar-with-dependencies.jar \
  --port 7000 --max-downloads 32 --max-uploads 5 \
  <file.torrent>
```

Verbose logging:

```bash
java -jar target/torrentino-0.1.0-beta-jar-with-dependencies.jar -v <file.torrent>
```

### CLI Options
The app uses PicoCLI. The most relevant options are:

- `-d, --download-dir <directory>`: Where to save files. Default: `~/Downloads/BitTorrent`
- `-p, --port <port>`: Base listening port (per torrent increments). Default: `6881`
- `-md, --max-downloads <count>`: Max concurrent download connections per torrent. Default: `48`
- `-mu, --max-uploads <count>`: Max concurrent upload connections per torrent. Default: `10`
- `-ai, --announce-interval <minutes>`: Tracker announce interval. Default: `1`
- `-v, --verbose`: Enable verbose logs
- `--help`, `--version`: Show help/version

Run `--help` to see all options and examples baked into the CLI.

## How It Works (high level)
1. Reads the `.torrent` file and contacts both **HTTP** and **UDP** trackers to discover peers.
2. Checks your disk for existing files and **verifies each piece** with SHA‑1.
3. Immediately **seeds** valid pieces; **queues** missing/corrupt ones for download.
4. Continues seeding after download completes, sending periodic tracker announcements.
5. If you stop the app, state is saved and **resumed on next start**.

## Safety & Integrity
- Files are verified piece‑by‑piece using the torrent’s **cryptographic SHA‑1** piece hashes.
- If corruption is detected, those pieces are **re-downloaded automatically**.
- This project does **not** include antivirus/malware scanning. Use your preferred antivirus if needed.

## Troubleshooting
- **No peers found**: Trackers may be down or the torrent may be inactive. Try again later or a different torrent.
- **Port already in use**: Pick a different base port using `--port <port>`.
- **Permission denied**: Ensure the `--download-dir` exists and is writable.

## Authors
- [@bihan001](https://github.com/bihan001)

---
Built with Java 17, Maven, Log4j, and PicoCLI.

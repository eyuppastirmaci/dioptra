# Dioptra

Dioptra is a Kotlin-based terminal UI application for inspecting, analyzing, and safely operating Redis databases.

It is designed for backend developers who want more visibility into Redis keyspaces directly from the terminal. Dioptra reads Redis runtime data, converts raw Redis output into structured information, and presents it through a TUI interface.

## What Dioptra Does

Dioptra connects to a Redis instance and provides terminal-based inspection screens for understanding Redis state and keyspace structure.

Current capabilities:

- Redis connection health check with `PING`
- Redis INFO dashboard
- Memory usage overview
- Connected client count
- Key count from Redis keyspace data
- Keyspace hit and miss metrics
- SCAN-based key browser
- Cursor-based key pagination
- Cancellable key scan loading
- Per-key `TYPE` display
- Per-key `TTL` display
- Per-key `MEMORY USAGE` display
- Windows development terminal support through Lanterna Swing terminal
- Native terminal rendering on Linux, WSL, and macOS through install distribution scripts
- File-based logging with Logback

Dioptra is useful when a developer wants Redis visibility without leaving the terminal.

## Key Features

### Available Now

- Redis INFO dashboard
- SCAN-based key browser
- Cursor-based key pagination
- Cancellable scan loading
- Display key type
- Display key TTL
- Display key memory usage
- File-based logging
- Reusable TUI theme and components
- Terminal backend selection for Windows, Linux, WSL, and macOS

### Planned For v0.1

- Pattern search
- Type-aware key detail screen
- STRING, HASH, LIST, SET, ZSET, and STREAM inspection
- JSON value auto-detection
- JSON pretty printing for string values
- Slowlog viewer
- Basic namespace summary
- Safe delete with confirmation
- Expire key action
- HOCON connection profile from `~/.dioptra/config.conf`
- Debug logging mode

### Planned For Later Versions

- Namespace memory analysis
- No-TTL key detection
- Big key detection
- Binary value detection
- Markdown report export
- Read-only mode
- Pub/Sub monitor
- Stream consumer group inspector
- Command latency dashboard
- Docker Compose Redis auto-detection
- SSH tunnel support
- TLS and auth support
- Basic Redis Cluster support
- GraalVM native-image distribution

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin/JVM |
| Build | Gradle |
| Redis client | Lettuce |
| TUI rendering | Lanterna |
| Concurrency | Kotlin Coroutines |
| Logging | SLF4J and Logback |
| Configuration | Planned HOCON support |
| Testing | Planned Kotest and Testcontainers Redis |

## Project Folder Structure

Current high-level structure:

```text
dioptra
├── app
│   ├── src
│   │   └── main
│   │       ├── kotlin
│   │       │   └── io.github.eyuppastirmaci.dioptra
│   │       │       ├── application
│   │       │       ├── bootstrap
│   │       │       ├── config
│   │       │       ├── domain
│   │       │       ├── infrastructure
│   │       │       └── presentation
│   │       └── resources
│   └── build.gradle.kts
├── buildSrc
├── gradle
├── utils
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
└── README.md
```

## Requirements

- JDK 25
- Docker Desktop or any reachable Redis instance
- Gradle Wrapper included in the project
- Windows Terminal, WSL, Linux terminal, or macOS terminal for native TUI testing

## Running Redis Locally With Docker

Start a local Redis instance:

```bash
docker run --name dioptra-redis -p 6379:6379 -d redis:7-alpine
```

## Requirements

- JDK 25
- Docker Desktop or any reachable Redis instance
- Gradle Wrapper included in the project
- Windows Terminal, WSL, Linux terminal, or macOS terminal for native TUI testing

## Running Redis Locally With Docker

Start a local Redis instance:

```bash
docker run --name dioptra-redis -p 6379:6379 -d redis:7-alpine
```

Verify Redis:

```bash
docker exec -it dioptra-redis redis-cli ping
```

Expected output:

```text
PONG
```

Add sample data:

```bash
docker exec -it dioptra-redis redis-cli
```

Then inside `redis-cli`:

```redis
SET user:1 "{\"id\":1,\"name\":\"John\"}"
EXPIRE user:1 3600

HSET session:abc userId 1 status active
LPUSH queue:emails job1 job2 job3
SADD tags:kotlin redis tui cli
ZADD leaderboard 100 john 80 ali
XADD events:orders * type created orderId 123
```

Exit Redis CLI:

```redis
exit
```

## Build

### Windows

```powershell
.\gradlew.bat :app:build
```

### Linux, WSL, macOS

```bash
./gradlew :app:build
```

## Run During Development

### Windows Development Mode

On Windows, Lanterna may open a Swing-based terminal window during development. This is the most stable development mode on Windows.

```powershell
.\gradlew.bat :app:run
```

### Linux, WSL, macOS Development Mode

For native terminal rendering, prefer the application distribution script instead of Gradle `run`.

```bash
./gradlew :app:installDist
./app/build/install/app/bin/app
```

This avoids TTY issues that can happen when running a terminal UI through Gradle's `run` task.

## WSL Test Workflow From Windows

If the main project is edited from Windows and native terminal rendering needs to be tested in WSL, sync the project into the Linux filesystem first:

```bash
rsync -a \
  --exclude .gradle \
  --exclude build \
  --exclude .idea \
  --exclude '**/build' \
  /mnt/c/Users/<windows-user>/IdeaProjects/dioptra/ \
  ~/projects/dioptra/
```

Then run:

```bash
cd ~/projects/dioptra
./gradlew :app:installDist
./app/build/install/app/bin/app
```

## Build A Runnable Distribution

Create an installable distribution.

### Windows

```powershell
.\gradlew.bat :app:installDist
```

Run the generated script:

```powershell
.\app\build\install\app\bin\app.bat
```

### Linux, WSL, macOS

```bash
./gradlew :app:installDist
```

Run the generated script:

```bash
./app/build/install/app/bin/app
```

## Logging

Dioptra uses file-based logging because stdout and stderr can break the terminal UI.

Default log path:

```text
~/.dioptra/logs/dioptra.log
```

On Windows, this usually maps to:

```text
C:\Users\<user>\.dioptra\logs\dioptra.log
```

## Current Keyboard Shortcuts

### Dashboard

| Key | Action |
|---|---|
| `k` | Open key browser |
| `q` | Exit |
| `ESC` | Exit |

### Key Browser

| Key | Action |
|---|---|
| `n` | Load next SCAN page |
| `r` | Refresh from cursor `0` |
| `ESC` | Cancel active scan while loading |
| `q` | Exit |

## License

This project is licensed under the MIT License.
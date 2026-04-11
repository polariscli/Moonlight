# Moonlight

A Minecraft Forge 1.8.9 mod that spoofs a Lunar Client connection, including peer detection, tab list icons, and the full Apollo protocol.

## Features

- **Client Brand Spoofing** - Appears as Lunar Client to servers, bypassing Forge/FML detection
- **Apollo Protocol** - Full [Apollo](https://lunarclient.dev/apollo/introduction) handshake with realistic per-user mod settings
- **Peer Detection** - Connects to Lunar's WebSocket to see which players are on Lunar Client
- **Tab List Icons** - Displays the Lunar icon next to peers in the tab list
- **Auto Version Sync** - Fetches the latest Lunar Client version automatically and caches it locally

## Building

```bash
./gradlew build
```

The built JAR will be in `build/libs/`. To bake the Lunar version into the build (CI):

```bash
eval $(python3 scripts/fetch_lunar_version.py)
./gradlew build -PlunarVersion=$lunarVersion -PlunarGitCommit=$lunarGitCommit
```

## Installation

1. Place the JAR in your `.minecraft/mods/` directory
2. Launch Minecraft with Forge 1.8.9
3. Connect to any server

On first launch, the mod fetches and caches the current Lunar Client version. The cache refreshes every 24 hours.

## License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for details.

## Disclaimer

This project is for educational and compatibility purposes. Use responsibly and in accordance with server rules.

# Moonlight

A Minecraft Forge mod that spoofs a Lunar Client connection to servers, implementing the Apollo protocol.

## Overview

Moonlight is a simple implementation of the [Apollo protocol](https://lunarclient.dev/apollo/introduction) that allows a Forge client to appear as Lunar Client to servers. This enables access to Apollo-enabled features and integrations on servers that support Lunar Client.

## Features

- Spoofs client brand to appear as Lunar Client
- Blocks FML handshake packets to appear as a vanilla client
- Registers Apollo plugin channels (`lunar:apollo` and `apollo:json`)
- Sends proper `PlayerHandshakeMessage` in Protocol Buffer format
- Handles incoming Apollo messages from servers (both Protobuf and JSON formats)
- Supports all major Apollo message types including waypoints, borders, notifications, titles, holograms, teams, staff mods, and more (currently only logs)

## Building

```bash
./gradlew build
```

The built JAR will be in `build/libs/`.

## Installation

1. Place the built JAR file in your Minecraft `mods/` directory
2. Launch Minecraft with Forge 1.8.9
3. Connect to any server - you will appear as Lunar Client

## Version Updates

Lunar Client version information (semver, git commit, etc.) is manually updated from time to time. To get the latest versions and hashes, you can use the [HandshakeCapture](https://github.com/polariscli/HandshakeCapture) plugin on a test server to capture real Lunar Client handshake data.

The version constants are located in `src/main/java/org/afterlike/moonlight/Moonlight.java` and can be updated based on captured handshake data.

## Apollo Protocol

This mod implements the Apollo protocol as documented at [lunarclient.dev/apollo](https://lunarclient.dev/apollo/introduction). Apollo enables server-side control of Lunar Client features and custom integrations.

## License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for details.

## Disclaimer

This mod is for educational and compatibility purposes. Use responsibly and in accordance with server rules and terms of service.


# QuantumPunish

[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Spigot Version](https://img.shields.io/badge/Minecraft-1.21.8-brightgreen.svg)](https://www.spigotmc.org/resources/your-plugin-link-here)

QuantumPunish is a comprehensive, feature-rich punishment plugin designed for Minecraft Spigot servers. It provides server staff with advanced tools for moderation, including a smart chat filter, automated warning system, intuitive history GUI, and seamless Discord integration.

## 📋 Features Overview

| Feature                       | Description                                                                                                                         |
|:------------------------------|:------------------------------------------------------------------------------------------------------------------------------------|
| **Complete Punishment Suite** | Permanent/Temporary Bans, IP Bans, Permanent/Temporary Mutes, Kicks, and Warnings.                                                  |
| **Automated Punishment**      | Configurable system to automatically apply harsher punishments based on accumulated warning points (e.g., 5 points = Kick).         |
| **Advanced Chat Filter**      | Detects prohibited words, including evasion techniques like character substitution (l33t speak) and repeated letters (`aaannjing`). |
| **Discord Webhook**           | Real-time, informative notifications for all punishment actions sent to a designated Discord channel.                               |
| **History GUI**               | Graphical User Interface for viewing a player's complete punishment history, featuring pagination and detailed information.         |
| **Player Info System**        | Comprehensive player tracking, including UUID, all known IP addresses, first join, last seen, and warning points.                   |
| **Self SQLite Database**      | Uses internal SQLite database to manage all persistent data, including player records and punishment history.                       |

## 📝 Commands & Permissions

All commands are controlled by the `quantumpunish` permission node.

| Command | Usage | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/ban` | `<player> <reason>` | `quantumpunish.ban` | Permanently ban a player. |
| `/tempban` | `<player> <reason> <duration>` | `quantumpunish.tempban` | Temporarily ban a player. |
| `/unban` | `<player>` | `quantumpunish.unban` | Pardon a player's ban. |
| `/banip` | `<player/ip> <reason>` | `quantumpunish.banip` | Ban a player's IP address. |
| `/unbanip` | `<ip>` | `quantumpunish.unbanip` | Unban an IP address. |
| `/mute` | `<player> <reason>` | `quantumpunish.mute` | Permanently mute a player. |
| `/tempmute` | `<player> <reason> <duration>` | `quantumpunish.tempmute` | Temporarily mute a player. |
| `/unmute` | `<player>` | `quantumpunish.unmute` | Unmute a player. |
| `/kick` | `<player> <reason>` | `quantumpunish.kick` | Kick a player from the server. |
| `/warn` | `<player> <reason>` | `quantumpunish.warn` | Issue a warning and add points. |
| `/history` | `<player>` | `quantumpunish.history` | View punishment history GUI. |
| `/qinfo` | `<player>` | `quantumpunish.info` | View comprehensive player info. |
| `/quantumpunish reload` | | `quantumpunish.admin` | Reload plugin configuration. |

### 🔐 Special Permissions

| Permission | Description |
| :--- | :--- |
| `quantumpunish.bypass.filter` | Bypasses the Chat Filter, allowing the player to use filtered words. |
| `quantumpunish.notify` | Allows the player to receive notifications when the Chat Filter blocks or replaces a message. |
| `quantumpunish.*` | Grants all QuantumPunish permissions. |

---

### ⏱️ Duration Format

The plugin uses a flexible duration parser (case-insensitive):

| Suffix | Unit | Default (No Suffix) | Example |
| :--- | :--- | :--- | :--- |
| `s` | Seconds | | `30s` (30 seconds) |
| `m` | Minutes | **Yes** | `10` or `10m` (10 minutes) |
| `h` | Hours | | `1h` (1 hour) |
| `d` | Days | | `7d` (7 days) |
| `w` | Weeks | | `2w` (2 weeks) |

## 🔧 Setup

QuantumPunish uses SQLite by default, requiring no setup other than placing the JAR file in your plugins folder.

1. Download the latest release from the [Spigot resource page](https://www.spigotmc.org/resources/quantumpunish-chat-filter-warning-points-temp-punish-more-1-21.130963/).
2. Place `QuantumPunish-1.0.0.jar` into your server's `plugins/` directory.
3. Start or restart your server.
4. **Configuration:** The plugin will generate default files (`config.yml`, `messages.yml`, `filter/filter.txt`, etc.) which you can customize before reloading (`/quantumpunish reload`).

## 👨‍💻 Author

Developed by **bintanq**.
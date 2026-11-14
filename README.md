<div align="center">

# MyCodes
### Promo Code System for Minecraft Networks

**One promo system for your whole network — proxy, backends, GUI, effects and Telegram.**

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/karonat1/MyCodes/releases)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.16.5--1.20.1-green.svg)](https://www.spigotmc.org/)
[![Platforms](https://img.shields.io/badge/platforms-BungeeCord%20%7C%20Velocity%20%7C%20Spigot%2FPaper-orange.svg)](#-installation)
[![License](https://img.shields.io/badge/license-MIT-lightgrey.svg)](LICENSE)

</div>

---

## What is MyCodes?

MyCodes is a full-featured promo code (promo / coupon) system for Minecraft servers and networks.
Create codes through a clean in-game GUI menu, hand out commands or items as rewards, decorate
activations with particle effects — and optionally tie everything to a Telegram bot for
anti-multiaccount protection and instant staff notifications.

- ✅ **Proxy + Backend:** BungeeCord / Waterfall / Velocity as the master, Spigot/Paper backends receive rewards via a plugin-message bridge.
- ✅ **Standalone mode:** works on a single Spigot/Paper server with no proxy at all.
- ✅ **Zero dependencies:** no Vault, no database required — JSON storage out of the box, MySQL optional.

---

## ✨ Key Features

### 🎛️ Full In-Game GUI Management
Everything is managed from the `/mcd menu` interface — no YAML editing required:
- Create, edit, enable/disable and delete promo codes.
- Choose reward type: **command** (console, `%player%` placeholder) or **item** (serialized safely).
- Set activation limits, expiry date, server restrictions, reward delay.
- Pick an activation effect and preview everything live.

### 🎆 14 Activation Effects
Every code can play a unique visual at the moment of activation:
`FIREWORK`, `FIREWORK_RAIN`, `HELIX`, `DOME`, `TOTEM`, `HEARTS`, `ENCHANT`,
`EXPLOSION`, `WATER`, `MYSTIC`, `LASER`, `FAIRY`, `DRAGON`, `PULSAR`.

### 📨 Telegram Integration (the highlight)
- **Anti-multiaccount protection:** one Telegram account = one Minecraft nick. Alts can never redeem a code.
- **One-click confirmation:** the player gets a personal link, presses Start in the bot — the code activates automatically.
- **Instant notifications:** every new promo code is announced to your admin chat with code, reward, server and limits.
- Setup takes two fields: `bot-token` from @BotFather and `admin-chat-id` (send `/chatid` to your bot).

### 💾 Flexible Storage
- **JSON files** — zero setup, works everywhere.
- **MySQL** — for large networks; data is shared through the proxy, backends stay stateless.

### 🌍 Multi-Language
Bundled **English**, **Russian** and **Turkish**. Switch with one line in `config.yml` (`language: ru`) and apply on the fly with `/mcd reload`.

### 🌐 Network-Aware
- Restrict a code to a specific backend server.
- Per-network usage counters are stored centrally on the proxy — no double-redemption across servers.
- Full config sync between proxy and backends via the bridge.

---

## 🚀 Installation

### Network setup (BungeeCord / Waterfall / Velocity)
1. Put **MyCodes-Bungee** (or **MyCodes-Velocity**) into the proxy's `plugins/` folder.
2. Put **MyCodes-Spigot** into every backend server's `plugins/` folder.
3. Restart the proxy first, then the backends.
4. Configure `plugins/MyCodes/config.yml` on the **proxy** (storage, language, Telegram).
5. Open `/mcd menu` in game and create your first code.

### Standalone setup (Spigot / Paper only)
1. Put **MyCodes-Spigot** into `plugins/`.
2. Restart the server.
3. Configure `plugins/MyCodes/config.yml` — done.

> **Client versions:** 1.16.5 – 1.20.1. Java 8+.

---

## 📖 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/mcd help` | `mycodes.use` | Show help |
| `/mcd menu` | `mycodes.menu` | Open the management GUI |
| `/mcd list [page]` | `mycodes.list` | List all promo codes |
| `/mcd reload` | `mycodes.reload` | Reload config & language (applies on the fly) |
| `/code <code>` | `mycodes.use` | Activate a promo code |
| `/promo <code>` | `mycodes.use` | Alias of `/code` |

**Permissions:** `mycodes.use` (default: everyone), `mycodes.menu`, `mycodes.list`, `mycodes.create`, `mycodes.edit`, `mycodes.delete`, `mycodes.reload` (default: op), and `mycodes.admin` (parent of all).

---

## ⚙️ Configuration Highlights

Everything lives in `config.yml` (on the proxy in network mode):

```yaml
language: en            # en | ru | tr

storage:
  type: file            # file (JSON) or mysql

telegram:
  enabled: false        # master switch
  bot-token: ''         # from @BotFather
  admin-chat-id: ''     # send /chatid to your bot
  require-confirmation: false   # global "confirm via Telegram" mode
  anti-multi: true      # one Telegram = one player

effects:
  default-type: FIREWORK
  default-duration: 3
  default-reward-delay: 0

broadcast-on-create: true
```

> Full reference with comments: [`mycodes-common/src/main/resources/config.yml`](mycodes-common/src/main/resources/config.yml)

---

## 🛠️ For Developers

### Building from Source
```bash
git clone https://github.com/karonat1/MyCodes.git
cd MyCodes
mvn clean package
```
- **Requirements:** JDK 8+, Maven 3.6+.
- **Artifacts:** `MyCodes-Bungee`, `MyCodes-Velocity`, `MyCodes-Spigot` in each module's `target/` folder.

### Project Structure
```
mycodes-parent
├── mycodes-common    # Core: config, storage (JSON/MySQL), promo logic, Telegram bot, bridge protocol, i18n
├── mycodes-bungee    # BungeeCord/Waterfall plugin: master data, commands, bridge server
├── mycodes-velocity  # Velocity plugin: master data, commands, bridge server
└── mycodes-spigot    # Spigot/Paper plugin: GUI menus, effects, reward dispatch, bridge client
```

### Architecture
- The **proxy owns the data**: codes, activations, settings. Backends stay stateless.
- Backend↔proxy communication uses a dedicated `mycodes:bridge` plugin-message channel with a JSON protocol.
- If no proxy is present, the Spigot plugin runs its own local core with the identical feature set.

---

## 📜 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<div align="center">

**Made with ❤️ for the Minecraft community.**

</div>

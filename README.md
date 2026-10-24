<div align="center">

# HanafubukiResourceWorld

**A resource world that resets on schedule — so the main world never turns into Swiss cheese.**

[![Build](https://github.com/Hanafubuki-minecraft/hanafubuki-resource-world/actions/workflows/release.yml/badge.svg)](https://github.com/Hanafubuki-minecraft/hanafubuki-resource-world/actions/workflows/release.yml)
[![Release](https://img.shields.io/github/v/release/Hanafubuki-minecraft/hanafubuki-resource-world)](https://github.com/Hanafubuki-minecraft/hanafubuki-resource-world/releases/latest)
[![License: GPL v3](https://img.shields.io/github/license/Hanafubuki-minecraft/hanafubuki-resource-world)](LICENSE)
![Paper](https://img.shields.io/badge/Paper-1.21.x-blue)

English · [Русский](README.ru.md)

</div>

---

## Features

- 🌍 **Separate resource world**, created and loaded by the plugin — no Multiverse needed
- 🔄 **Scheduled reset** — every N days the world is wiped and regenerated with a new seed (configurable)
- ⏰ **Personal reminders** — starting N days before the reset, every player sees how much time is left when they join; `/rwtime` any time
- 🛏️ **Beds don't set respawn there** — so `/home`-by-bed setups keep pointing to the main world
- 🚫 **Nether/End portals disabled** in the resource world — no shortcuts into the main Nether
- 🌀 **Optional portal that works like a Nether portal** — stand in it and the screen starts to sway, after a few seconds you travel; step out earlier and nothing happens. Vertical (an arch) or horizontal (a floor). Cherry petals drift through it over a soft pink veil
- 🎯 **Safe random landing** — async chunk loading, avoids water, lava and leaves
- 🧱 **World border** and optional **Chunky** pregeneration after each reset
- 🌐 English by default, Russian built in, add your own language file

## How players get there

- **Portal** — enable it in the config and mark the area in-game (see below).
- **Command** — on Hanafubuki this is `/resources` from [hanafubuki-core-plugin](https://github.com/Hanafubuki-minecraft/hanafubuki-core-plugin), with the same warm-up as `/spawn` and `/home`. Any teleport plugin that can send players to a world named `resources` works too.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/rwtime` | Time left until the reset | `hanafubuki.rw.info` (everyone) |
| `/rwadmin info` | World, next reset date, portal status | `hanafubuki.rw.admin` (op) |
| `/rwadmin reset` | Reset now (asks for confirmation) | `hanafubuki.rw.admin` |
| `/rwadmin portal pos1 \| pos2 \| save \| enable \| disable \| clear` | Set up the portal area | `hanafubuki.rw.admin` |
| `/rwadmin language [code]` | Show or change the language | `hanafubuki.rw.admin` |
| `/rwadmin reload` | Reload config and language files | `hanafubuki.rw.admin` |

## Setting up the portal

1. Build an arch (or a floor) at spawn from any blocks.
2. Look at one corner of the inside and run `/rwadmin portal pos1`, then the opposite corner — `/rwadmin portal pos2`.
3. `/rwadmin portal save` — the area is saved and the portal is enabled. Turn it off/on any time with `/rwadmin portal disable` / `enable`.

## Configuration

```yaml
language: en
world:
  name: resources
  environment: NORMAL        # NORMAL, NETHER, THE_END
  border-size: 8000          # 0 = no border
reset:
  interval-days: 30
  notify-before-days: 3
  pregenerate-with-chunky: false
random-teleport:
  min-radius: 200
  radius: 3000
  max-attempts: 12
rules:
  block-bed-spawn: true
  block-nether-portals: true
portal:
  enabled: false
  charge-seconds: 4          # stand in the portal this long to travel
  cooldown-seconds: 5
  effects:
    enabled: true
    petals: true
    veil: true
    veil-color: "#F7A1C0"
    sound: true
    view-distance: 32
```

> A reset unloads and deletes the world folder and generates a new one. It can freeze the server for a few seconds — if the reset date passes while the server is off, it happens on the next start, before anyone joins.

## Requirements

Paper 1.21.x, Java 21. Optional: [Chunky](https://modrinth.com/plugin/chunky).

## Releasing

Bump `version` in [`gradle.properties`](gradle.properties) and push to `main` — GitHub Actions builds the jar and publishes release `v<version>`.

## License

[GNU General Public License v3.0](LICENSE) © ByteMe6

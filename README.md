# MC BOT — Superhuman Minecraft Bot Mod

A complete Fabric client mod for Minecraft **26.1.2** with hardcoded AI modules + Claude AI brain.

---

## Quick Start

1. **Install Fabric Loader** for Minecraft 26.1.2 at [fabricmc.net](https://fabricmc.net)
2. **Download Baritone** (Fabric version) from [github.com/cabaletta/baritone/releases](https://github.com/cabaletta/baritone/releases) and drop it in `.minecraft/mods/`
3. **Double-click `setup.bat`** — it auto-installs Java 21 if needed, then builds and installs the mod
4. **Launch Minecraft** with Fabric and your world

---

## Controls

| Key | Action |
|-----|--------|
| `Right Shift` | Open GUI panel |
| `K` | Toggle KillAura |
| `C` | Toggle AutoCrystal |
| `V` | Toggle ElytraFlight |
| `END` | PANIC — stop everything |

---

## Chat Commands

Type in chat (never sent to server):

```
.bot help                    — list all commands
.bot mine <ore>              — mine: diamond/iron/gold/emerald/ancient/coal/obsidian
.bot goto <x> <z>            — navigate to coordinates
.bot build <schematic>       — build from .nbt schematic
.bot farm                    — toggle auto farm
.bot explore                 — explore the world
.bot killaura                — toggle KillAura
.bot crystal                 — toggle AutoCrystal
.bot mace                    — toggle MaceCombat
.bot elytra                  — toggle ElytraFlight
.bot shield                  — toggle AutoShield
.bot brain <goal>            — AI brain: natural language goal
.bot stop                    — stop everything
.bot status                  — show active modules
```

### AI Brain example
```
.bot brain get me ready for the End fight
.bot brain mine diamonds until I have a full set then come back
.bot brain explore the map and scout for villages
```

---

## Modules

### Combat
| Module | Description |
|--------|-------------|
| **KillAura** | Auto-target, full-charge hits, W-tap, jump-crits, strafing |
| **AutoCrystal** | Places and pops End Crystals at superhuman speed (~20/sec) |
| **MaceCombat** | State machine: ascend 20 blocks → dive → smash target |
| **ElytraStrike** | Aerial sword hits while gliding at speed |
| **AutoShield** | Blocks incoming projectiles and melee; releases to attack |

### World
| Module | Description |
|--------|-------------|
| **AutoMine** | Baritone-powered ore mining with ore presets |
| **AutoFarm** | Scans radius, harvests mature crops, replants seeds |
| **Builder** | Builds from .nbt schematics via Baritone |
| **Exploration** | Outward spiral world exploration |

### Player
| Module | Description |
|--------|-------------|
| **AutoEat** | Eats food when hunger drops below 80% |
| **AutoArmor** | Auto-equips best armour from inventory |
| **SpeedBridge** | Places blocks while walking — superhuman bridging |

### Movement
| Module | Description |
|--------|-------------|
| **ElytraFlight** | Auto-deploys elytra, altitude hold, firework boosts |

### AI Brain
| Module | Description |
|--------|-------------|
| **BotBrain** | Natural language goal → Claude API → task sequence |

---

## Claude API (AI Brain)

1. Get your free key at [console.anthropic.com](https://console.anthropic.com)
2. Open `src/main/java/com/mcbot/ai/ClaudeClient.java`
3. Paste your key on line: `private static final String API_KEY = "YOUR_KEY_HERE";`
4. Re-run `setup.bat` to rebuild

Uses `claude-haiku` — cheapest model, typically < $0.001 per command.

Alternatively, set environment variable `MC_BOT_CLAUDE_KEY` and you never have to touch the code.

---

## Building Schematics

Place `.nbt` schematic files in `.minecraft/schematics/` then:
```
.bot build my_house
```

The bot will navigate to your current position and build the structure using Baritone.

---

## Project Structure

```
MC BOT/
├── setup.bat                    ← Run this first
├── build.gradle
├── gradle.properties
├── settings.gradle
└── src/main/java/com/mcbot/
    ├── MCBotClient.java          ← Entry point
    ├── ai/
    │   ├── BotBrainModule.java   ← AI module
    │   ├── ClaudeClient.java     ← API calls
    │   ├── Task.java
    │   └── TaskQueue.java
    ├── command/
    │   └── BotCommandHandler.java
    ├── gui/
    │   ├── BotScreen.java        ← Right-Shift GUI
    │   └── HudOverlay.java       ← Active module list
    ├── mixin/                    ← Minecraft hooks
    ├── module/
    │   ├── combat/               ← KillAura, AutoCrystal, Mace, Elytra, Shield
    │   ├── movement/             ← ElytraFlight
    │   ├── player/               ← AutoEat, AutoArmor, SpeedBridge
    │   └── world/                ← AutoMine, AutoFarm, Builder, Exploration
    └── util/                     ← EntityUtil, CombatUtil, InventoryUtil, BlockUtil
```

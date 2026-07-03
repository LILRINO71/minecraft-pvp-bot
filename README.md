# MC BOT — Superhuman Minecraft Bot Mod

A client-side Fabric mod for Minecraft **26.1.2**: a modular combat / utility / automation bot with a
click-to-toggle GUI, a live HUD, entity ESP, Baritone-powered pathing, and an optional Claude AI "brain."

Built against Mojang official mappings, Java 25, Fabric Loom 1.16.

---

## Quick Start

1. **Install Fabric Loader** for Minecraft 26.1.2 (via your launcher or [fabricmc.net](https://fabricmc.net)).
2. **Install Fabric API** (matching 26.1.2) in `.minecraft/mods/`.
3. **Install Baritone for 26.1** — use Meteor's actively-maintained fork
   (`meteordevelopment:baritone`, bundled inside [Meteor Client](https://meteorclient.com) or from
   [maven.meteordev.org](https://maven.meteordev.org)). The old `baritone-api-fabric-1.15` is for 1.21 and
   will **not** load on 26.1.2.
4. **Double-click `setup.bat`** — checks for Java 25, sets up the Gradle 9.4.1 wrapper, and builds the mod.
5. Copy `build/libs/mcbot-1.0.0.jar` into `.minecraft/mods/` and launch.

> **Requires Java 25** (Minecraft 26.1.2's toolchain). `setup.bat` will tell you if it's missing.

---

## Controls

| Key | Action |
|-----|--------|
| `Right Shift` | Open the **ClickGUI** (click any module to toggle it) |
| `K` | Toggle KillAura |
| `C` | Toggle AutoCrystal |
| `M` | Toggle MaceCombat |
| `P` | Toggle AutoPearl |
| `H` | Toggle Grapple |
| `J` | Toggle SilentAim |
| `R` | Toggle TriggerBot |
| `O` | Toggle AutoTotem |
| `U` | Toggle Velocity (anti-knockback) |
| `V` | Toggle ElytraFlight |
| `G` | Mark entity you're looking at as friend / foe (cycles) |
| `END` | **PANIC** — disable everything and cancel Baritone |

A **HUD arraylist** (top-right) shows every enabled module in real time, and **EntityESP** draws colored
boxes around entities (foe = red, friend = green, neutral players = white, hostile mobs = orange).

---

## Chat Commands

Type in chat with the **`#bot`** prefix (handled locally, never sent to the server — the `.` prefix is left
free for Meteor Client):

```
#bot help                    — list all commands
#bot status                  — show active modules
#bot mine <ore>              — mine: diamond/iron/gold/emerald/ancient/coal/obsidian
#bot goto <x> <z>            — navigate to coordinates
#bot killaura                — toggle KillAura
#bot crystal                 — toggle AutoCrystal
#bot mace                    — toggle MaceCombat
#bot pearl                   — toggle AutoPearl
#bot grapple                 — toggle Grapple
#bot silentaim               — aim server-side, camera stays still
#bot trigger                 — auto-attack the entity under your crosshair
#bot totem                   — keep a Totem of Undying in your off-hand
#bot velocity                — reduce knockback taken (anti-knockback)
#bot elytra                  — toggle ElytraFlight
#bot shield                  — toggle AutoShield
#bot farm                    — toggle auto farm
#bot explore                 — explore the world
#bot brain <goal>            — AI brain: natural-language goal
#bot stop                    — stop everything
```

### AI Brain example
```
#bot brain get me ready for the End fight
#bot brain mine diamonds until I have a full set then come back
```

---

## Modules

### Combat
| Module | Description |
|--------|-------------|
| **KillAura** | Auto-target, circle-strafe, sprint-reset knockback, jump-crit timing, best-weapon auto-switch |
| **AutoCrystal** | Places an End Crystal on a valid obsidian/bedrock base near the target, then pops it |
| **MaceCombat** | Elytra deploy → rocket-boosted ascent → steep dive → mace smash with a damage readout |
| **AutoPearl** | Pearl-catches runners, escape-pearls when low, tracks enemy pearls in flight |
| **Grapple** | Fishing-rod hook that yanks a target back into melee range |
| **SilentAim** | Sends aim to the server via a rotation packet — hits land, camera never turns (pairs with KillAura) |
| **TriggerBot** | Auto-attacks the entity under your crosshair when charged; respects friend/foe filter |
| **AutoShield** | Blocks incoming projectiles/melee; releases to attack |

### World
| Module | Description |
|--------|-------------|
| **AutoMine** | Baritone-powered ore mining with ore presets |
| **AutoFarm** | Scans radius, harvests mature crops, replants |
| **Builder** | Builds from `.nbt` schematics via Baritone |
| **Exploration** | Outward-spiral world exploration |

### Player
| Module | Description |
|--------|-------------|
| **AutoEat** | Eats when hunger drops |
| **AutoArmor** | Auto-equips best armour from inventory |
| **SpeedBridge** | Places blocks while walking — fast bridging |
| **AutoTotem** | Keeps a Totem of Undying in your off-hand automatically |

### Movement / Render / AI
| Module | Description |
|--------|-------------|
| **ElytraFlight** | Auto-deploys elytra, altitude hold, firework boosts |
| **Velocity** | Reduces knockback taken when hit (anti-knockback) |
| **EntityESP** | Colored outline boxes around living entities |
| **BotBrain** | Natural-language goal → Claude API → task sequence |

---

## Claude API (AI Brain)

The AI brain is **optional** — every other module works without it.

- **Recommended:** set the environment variable `MC_BOT_CLAUDE_KEY` to your key. No rebuild, key never
  touches the source (and never lands in the repo).
- Or paste it into `src/main/java/com/mcbot/ai/ClaudeClient.java` (`API_KEY = "..."`) and re-run `setup.bat`.

Get a key at [console.anthropic.com](https://console.anthropic.com).

---

## Tech Notes

- **Minecraft 26.1.2** on Mojang official mappings (26.1 dropped yarn/intermediary).
- **No mixins** — chat interception uses Fabric's `ClientSendMessageEvents`, the tick loop uses
  `ClientTickEvents`, the HUD uses `HudElementRegistry`, and ESP uses `LevelRenderEvents` — all stable
  Fabric API surfaces rather than fragile bytecode injection.
- **Baritone** is a `compileOnly` dependency (Meteor's 26.1 fork); the runtime jar lives in your mods folder.

## Project Structure

```
src/main/java/com/mcbot/
├── MCBotClient.java          ← Entry point: keybinds, tick loop, event wiring
├── ai/                       ← BotBrainModule, ClaudeClient, Task, TaskQueue
├── command/                  ← BotCommandHandler (#bot ...)
├── gui/
│   ├── BotScreen.java        ← Right-Shift ClickGUI (click to toggle)
│   └── HudOverlay.java       ← Enabled-module HUD arraylist
├── module/
│   ├── combat/               ← KillAura, AutoCrystal, Mace, AutoPearl, Grapple, Elytra, Shield
│   ├── movement/             ← ElytraFlight
│   ├── player/               ← AutoEat, AutoArmor, SpeedBridge
│   ├── render/               ← EntityESP
│   └── world/                ← AutoMine, AutoFarm, Builder, Exploration
├── targeting/                ← FriendList, TargetConfig
└── util/                     ← EntityUtil, CombatUtil, InventoryUtil, BlockUtil, MovementUtil
```

# MC BOT — Roadmap to a Fully Autonomous Player

**North star:** an agent that can play Minecraft end-to-end — survive, fight, gather,
craft, travel, trade, and pursue open-ended goals ("get maxed netherite armor", "build a
villager trading hall") — with movement and timing better than a human.

This is a hard, long-horizon problem (the same one research agents like **Voyager** and
**MineRL** tackle). This doc is the honest plan for getting there in stages, and where the
project stands today.

---

## Architecture

The bot is a layered agent inside the Minecraft client:

```
 High-level goal  ──►  BotBrain (LLM planner)          ← natural language → task list
        │
        ▼
   TaskQueue / Task  ──►  Executors                    ← "mine 64 iron", "craft chestplate"
        │
        ▼
   Modules  ──►  reactive behaviors                    ← KillAura, AutoClutch, AutoEat, ...
        │
        ▼
   Baritone (pathing) + Minecraft client APIs          ← movement, world, inventory, packets
```

- **Modules** — reactive, always-on behaviors (combat, survival, movement, render). ~30 today.
- **Settings** — every module is tunable (GUI submenu + `#bot set`), values persist to disk.
- **Baritone** — handles pathfinding, mining, and travel (Meteor's 26.1 fork).
- **BotBrain + TaskQueue** — the autonomy layer: turns a goal into an ordered task list.

---

## Phases

### ✅ Phase 1 — Reactive skills (mostly done)
The "reflexes" a good player has, each perfect-timed and tunable.
- Combat: KillAura, AutoCrystal (place obsidian + crystal + pop), MaceCombat (elytra dive),
  SilentAim (smooth), TriggerBot, Surround, AutoPearl, Grapple.
- Survival: AutoEat, AutoTotem, AutoArmor, **AutoClutch** (MLG water bucket), NoFall, Velocity.
- Movement: AutoSprint, Step, SafeStep, ElytraFlight.
- Awareness: EntityESP, Tracers, HUD, Fullbright.

### 🔨 Phase 2 — Autonomous survival loop (in progress)
One supervisor that keeps the bot alive without babysitting.
- ✅ **Survival module** — composes eat + totem + clutch + mob-defense into one toggle.
- ⏳ Threat response: flee-with-pearls when low, pick fight-vs-flee by health/gear/enemy count.
- ⏳ Hazard avoidance: lava/void/fall awareness woven into movement.

### 🔭 Phase 3 — Task executors (the core of "do things for me")
Concrete, verifiable sub-goals the planner can call. Each is a state machine over Baritone +
inventory + crafting:
- `GatherTask(item, count)` — mine/collect until you have N (Baritone + pickup tracking).
- `CraftTask(item)` — resolve the recipe tree, gather ingredients, drive the crafting-table GUI.
- `TravelTask(x,z / biome / structure)` — route planning and long-distance travel.
- `SmeltTask`, `StoreTask`, `EquipTask`.
> Hardest part: **GUI automation** (crafting tables, furnaces, villager trades) — driving
> container menus reliably via `handleContainerInput`, plus a recipe/dependency resolver.

### 🌆 Phase 4 — Composite goals (your examples)
Built by composing Phase 3 tasks:
- **"Get maxed armor"** → gather diamonds → craft armor → travel Nether → gather netherite →
  smith → enchant (needs an EnchantTask + XP farming).
- **"Villager trading hall"** → plan layout → gather materials → build cells (schematic engine) →
  breed/transport villagers → cycle trades. This one is genuinely large.

### 🧠 Phase 5 — LLM planner
BotBrain (Claude) decomposes an open-ended goal into Phase 3/4 tasks, watches results, and
re-plans on failure (the Voyager-style loop). The reactive modules keep it alive while it thinks.

---

## Honest risk list
- **GUI/crafting/trading automation** is the biggest unknown — finicky and version-sensitive.
- **Long-horizon planning** (recovering from death, adapting to terrain) is an open research area.
- **Anti-cheat**: packet tricks (silent aim, NoFall) are fine in singleplayer but flagged on some
  servers — kept behind toggles.
- Everything is verified to **compile against 26.1**, but in-game behavior needs real testing;
  crash reports drive the fixes.

**Current focus:** finish Phase 2, then build the first three Phase 3 executors
(`GatherTask`, `TravelTask`, `CraftTask`) — that's the unlock for real autonomy.

# Alloy Mod Conversion Guide

A comprehensive guide for converting existing Minecraft mods to the Alloy modding ecosystem, and for building new mods from scratch.

---

## Table of Contents

1. [Alloy API Reference](#alloy-api-reference)
2. [Permission System](#permission-system)
3. [Mod Descriptor: `alloy.mod.json`](#mod-descriptor-alloymodjson)
4. [Converting from Bukkit/Spigot/Paper](#converting-from-bukkitspigotpaper)
5. [Converting from Fabric](#converting-from-fabric)
6. [Converting from Forge](#converting-from-forge)
7. [Mapping Tables](#mapping-tables)
8. [Common Pitfalls](#common-pitfalls)
9. [Build & Test Workflow](#build--test-workflow)

---

## Alloy API Reference

### Core Architecture

Alloy follows a strict layered architecture:

```
Your Mod → Alloy API → Alloy Loader → Minecraft (deobfuscated)
```

**Golden rule**: Mods NEVER import Minecraft classes directly. Everything goes through `net.alloymc.api.*`. When Minecraft updates, Alloy absorbs the changes — your mod stays untouched.

### Entry Point

Every mod implements `ModInitializer`:

```java
package com.example.mymod;

import net.alloymc.loader.api.ModInitializer;

public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Register blocks, items, events, commands here
    }
}
```

### AlloyAPI (Central Access)

All services are accessed through `net.alloymc.api.AlloyAPI`:

```java
import net.alloymc.api.AlloyAPI;

// Access services during or after onInitialize()
AlloyAPI.server();              // Server instance
AlloyAPI.eventBus();            // Event dispatcher
AlloyAPI.commandRegistry();     // Command registration
AlloyAPI.permissionRegistry();  // Permission node registration
AlloyAPI.economy();             // Economy provider registry
AlloyAPI.scheduler();           // Task scheduling
AlloyAPI.environment();         // CLIENT or SERVER
```

### Event System

#### Registering Listeners

```java
import net.alloymc.api.AlloyAPI;
import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.EventPriority;
import net.alloymc.api.event.Listener;

public class MyListener implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Handle event
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Only fires if not cancelled by lower-priority handlers
    }
}

// In your onInitialize():
AlloyAPI.eventBus().register(new MyListener());
```

#### Event Priorities (lowest fires first)

| Priority | Use Case |
|----------|----------|
| `LOWEST` | Monitoring/logging — observe but don't modify |
| `LOW` | Early processing |
| `NORMAL` | Default — most handlers use this |
| `HIGH` | Override decisions from lower priorities |
| `HIGHEST` | Final say — cancellation/uncancellation |

#### Firing Custom Events

```java
MyCustomEvent event = new MyCustomEvent(someData);
AlloyAPI.eventBus().fire(event);
if (event.isCancelled()) {
    // Another handler cancelled it
}
```

### Command System

```java
import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandSender;

import java.util.List;

public class HealCommand extends Command {

    public HealCommand() {
        super("heal", "Heal a player", "mymod.command.heal", List.of("h"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.isPlayer()) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        sender.sendMessage("You have been healed!");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String label, String[] args) {
        // Return tab completions
        return List.of();
    }
}

// Register in onInitialize():
AlloyAPI.commandRegistry().register(new HealCommand());
```

### Permission System

#### Registering Permission Nodes

```java
import net.alloymc.api.permission.PermissionRegistry;
import net.alloymc.api.permission.PermissionRegistry.PermissionDefault;

PermissionRegistry registry = AlloyAPI.permissionRegistry();

// Defaults to OP-only
registry.register("mymod.command.heal", "Allow healing");

// Explicitly set default
registry.register("mymod.feature.chat", "Use chat features", PermissionDefault.TRUE);
registry.register("mymod.admin.reload", "Reload config", PermissionDefault.OP);
registry.register("mymod.bypass.limit", "Bypass limits", PermissionDefault.FALSE);
```

#### PermissionDefault Values

| Value | Meaning |
|-------|---------|
| `TRUE` | Granted to all players by default |
| `FALSE` | Denied to all players by default |
| `OP` | Only granted to operators |

#### Checking Permissions

```java
// On any CommandSender or PermissionHolder:
if (sender.hasPermission("mymod.command.heal")) {
    // allowed
}

if (sender.isOp()) {
    // operator
}
```

#### Custom Permission Provider

Replace the default file-based provider:

```java
import net.alloymc.api.permission.PermissionProvider;

public class MyPermProvider implements PermissionProvider {
    @Override
    public boolean hasPermission(UUID playerId, String playerName, String permission) {
        // Your logic (database, Redis, etc.)
        return false;
    }

    @Override
    public boolean isOp(UUID playerId) {
        return false;
    }

    @Override
    public void onEnable() {
        // Called when this provider becomes active
    }

    @Override
    public void onDisable() {
        // Called when replaced by another provider
    }
}

// In onInitialize():
AlloyAPI.permissionRegistry().setProvider(new MyPermProvider());
```

### Economy System

Alloy includes a built-in economy API. The default provider (`FileEconomyProvider` from `alloy-core`) stores balances in `economy.json` with a configurable starting balance. Any mod can replace it with a custom implementation.

#### Using the Economy

```java
import net.alloymc.api.AlloyAPI;
import net.alloymc.api.economy.EconomyProvider;

EconomyProvider economy = AlloyAPI.economy().provider();

// Check if economy is available
if (AlloyAPI.economy().isAvailable()) {
    double balance = economy.getBalance(playerId);
    economy.deposit(playerId, 50.0);
    boolean success = economy.withdraw(playerId, 25.0);
    boolean hasEnough = economy.has(playerId, 100.0);
    boolean transferred = economy.transfer(fromId, toId, 10.0);
    economy.setBalance(playerId, 500.0);
}
```

#### Custom Economy Provider

Replace the default file-based provider with your own (database, Redis, etc.):

```java
import net.alloymc.api.economy.EconomyProvider;

public class MySQLEconomyProvider implements EconomyProvider {
    @Override
    public double getBalance(UUID playerId) {
        // Query your database
    }

    @Override
    public void setBalance(UUID playerId, double amount) {
        // Update database
    }

    @Override
    public void deposit(UUID playerId, double amount) {
        // Add to balance in database
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        // Atomically check and subtract
    }

    @Override
    public boolean has(UUID playerId, double amount) {
        return getBalance(playerId) >= amount;
    }

    @Override
    public boolean transfer(UUID from, UUID to, double amount) {
        // Atomic transfer in database transaction
    }

    @Override
    public void onEnable() {
        // Open database connections
    }

    @Override
    public void onDisable() {
        // Close connections, flush caches
    }
}

// In onInitialize():
AlloyAPI.economy().setProvider(new MySQLEconomyProvider());
```

When you call `setProvider()`:
1. The old provider's `onDisable()` is called (cleanup)
2. Your provider becomes active
3. Your provider's `onEnable()` is called
4. All existing commands (`/balance`, `/pay`, `/setmoney`, `/buyclaimblocks`) automatically use your provider — no re-registration needed

The replacement is logged to the console so server admins can see which economy provider is active.

#### Built-In Economy Commands

| Command | Permission | Default | Description |
|---------|-----------|---------|-------------|
| `/balance [player]` | `alloy.command.balance` | All players | Check your balance (ops can check others) |
| `/pay <player> <amount>` | `alloy.command.pay` | All players | Transfer money to another player |
| `/setmoney <player> <amount>` | `alloy.command.setmoney` | OP only | Set a player's exact balance |
| `/buyclaimblocks <amount>` | `griefprevention.buyclaimblocks` | All players | Purchase claim blocks with currency |
| `/giveclaimblocks <player> <amount>` | `griefprevention.giveclaimblocks` | OP only | Give free claim blocks to a player |

Aliases: `/bal`, `/money` for balance; `/setbalance`, `/setbal` for setmoney; `/buyclaim` for buyclaimblocks; `/gcb` for giveclaimblocks.

### Scheduler

```java
import net.alloymc.api.AlloyAPI;
import net.alloymc.api.scheduler.ScheduledTask;

// Run next tick
AlloyAPI.scheduler().runTask(() -> { /* sync work */ });

// Run after delay (ticks — 20 ticks = 1 second)
AlloyAPI.scheduler().runTaskLater(() -> { /* ... */ }, 40); // 2 seconds

// Run repeating (delay, then period)
ScheduledTask task = AlloyAPI.scheduler().runTaskTimer(() -> {
    // Runs every second after 1-second delay
}, 20, 20);

// Cancel later
task.cancel();

// Async versions (off main thread)
AlloyAPI.scheduler().runAsync(() -> { /* database query, HTTP call, etc. */ });
AlloyAPI.scheduler().runAsyncLater(() -> { /* ... */ }, 100);
AlloyAPI.scheduler().runAsyncTimer(() -> { /* ... */ }, 0, 200);
```

### Server Interface

```java
import net.alloymc.api.AlloyAPI;
import net.alloymc.api.Server;

Server server = AlloyAPI.server();

server.onlinePlayers();          // All online players
server.player(uuid);             // Find by UUID
server.player("Steve");          // Find by name
server.worlds();                 // All loaded worlds
server.world("overworld");       // Find world by name
server.dataDirectory();          // Server data dir (Path)
server.logger();                 // Java Logger
server.currentTick();            // Current tick count
server.broadcast("Hello all!");  // Message all players
```

### Launch Environment

```java
import net.alloymc.api.AlloyAPI;
import net.alloymc.api.LaunchEnvironment;

LaunchEnvironment env = AlloyAPI.environment();

if (env == LaunchEnvironment.CLIENT) {
    // Client-only code (UI, rendering)
}

if (env == LaunchEnvironment.SERVER) {
    // Server-only code
}

// Check if a mod's environment is compatible
env.shouldLoad("both");    // true
env.shouldLoad("client");  // true only on CLIENT
env.shouldLoad("server");  // true only on SERVER
```

---

## Permission System

### Architecture

Alloy uses a **hybrid approach**: a tightly integrated default + pluggable backend.

```
Permission Check Flow:
  1. User-specific permission overrides
  2. User's groups (with parent inheritance, cycle-safe)
  3. Default group
  4. PermissionDefault fallback (TRUE/FALSE/OP)
```

### File-Based Provider (Default)

The default `FilePermissionProvider` (from `alloy-core`) reads `permissions.json` from the server's data directory.

#### Default `permissions.json`

```json
{
  "groups": {
    "default": {
      "permissions": ["alloy.command.mods", "alloy.command.alloy"],
      "parents": []
    },
    "moderator": {
      "permissions": ["alloy.command.tps"],
      "parents": ["default"]
    },
    "admin": {
      "permissions": ["*"],
      "parents": ["moderator"]
    }
  },
  "users": {}
}
```

#### Adding Users

```json
{
  "users": {
    "Steve": {
      "groups": ["moderator"],
      "permissions": {
        "mymod.special.feature": true
      },
      "op": false
    },
    "00000000-0000-0000-0000-000000000001": {
      "groups": ["admin"],
      "permissions": {},
      "op": true
    }
  }
}
```

Users can be identified by name or UUID. Permission overrides take precedence over group permissions.

#### Wildcard Permissions

- `"*"` — matches everything
- `"alloy.command.*"` — matches all `alloy.command.` prefixed permissions

#### In-Game Management

```
/permissions reload                              — Reload from disk
/permissions groups                              — List all groups
/permissions group <name>                        — Show group details
/permissions user <name>                         — Show user permissions
/permissions user <name> addgroup <group>        — Add user to group
/permissions user <name> removegroup <group>     — Remove from group
/permissions user <name> set <perm> <true|false> — Set override
```

---

## Mod Descriptor: `alloy.mod.json`

Every Alloy mod JAR must contain an `alloy.mod.json` at the JAR root.

### Full Specification

```json
{
  "id": "my-mod",
  "name": "My Awesome Mod",
  "version": "1.0.0",
  "description": "Does awesome things",
  "authors": ["Alice", "Bob"],
  "license": "MIT",
  "entrypoint": "com.example.mymod.MyMod",
  "dependencies": {
    "minecraft": ">=1.21.0",
    "alloy": ">=0.1.0",
    "other-mod": ">=2.0.0"
  },
  "environment": "both"
}
```

### Field Reference

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `id` | Yes | string | Unique mod identifier. Lowercase alphanumeric + hyphens. E.g., `"my-mod"` |
| `name` | Yes | string | Human-readable display name |
| `version` | Yes | string | Semantic version (major.minor.patch). E.g., `"1.0.0"` |
| `description` | No | string | Short description |
| `authors` | No | string[] | List of author names |
| `license` | No | string | License identifier (e.g., `"MIT"`, `"Apache-2.0"`) |
| `entrypoint` | No | string | Fully-qualified class name implementing `ModInitializer`. Omit for library mods. |
| `dependencies` | No | object | Map of mod/system ID to version constraint |
| `environment` | No | string | `"client"`, `"server"`, or `"both"` (default: `"both"`) |

### ID Format Rules

- Lowercase letters, digits, and hyphens only: `[a-z0-9][a-z0-9-]*[a-z0-9]`
- Single-character IDs are allowed: `[a-z0-9]`
- Examples: `my-mod`, `alloy-core`, `x`

### Version Constraints

Used in `dependencies`:

| Constraint | Meaning |
|-----------|---------|
| `"*"` | Any version |
| `">=1.0.0"` | 1.0.0 or higher |
| `">1.0.0"` | Higher than 1.0.0 |
| `"<=2.0.0"` | 2.0.0 or lower |
| `"<2.0.0"` | Lower than 2.0.0 |
| `"=1.5.0"` | Exactly 1.5.0 |

### Special Dependency Keys

| Key | Meaning |
|-----|---------|
| `"minecraft"` | Minecraft version constraint |
| `"alloy"` | Alloy loader version constraint |
| Any other key | Another mod's `id` |

### Environment Values

| Value | Loaded On |
|-------|-----------|
| `"client"` | Client only |
| `"server"` | Dedicated server only |
| `"both"` | Client and server (default) |

---

## API Implementation Status

### Entity Type Hierarchy

The entity wrapper factory (`EntityWrapperFactory`) inspects each Minecraft entity's class hierarchy at runtime and returns the correct Alloy wrapper type. This ensures `instanceof` checks work correctly in mod code.

| Alloy Type | MC Class (1.21.11) | `instanceof` Works | Key Methods |
|---|---|---|---|
| `Player` | `axg` (ServerPlayer) | Yes | UUID, name, health, inventory, gamemode, teleport, kick, permissions |
| `LivingEntity` | `chl` (LivingEntity) | Yes | health, maxHealth, setHealth, damage, isDead, target |
| `Projectile` | `dec` (Projectile) | Yes | shooter |
| `TameableEntity` | `cii` (TamableAnimal) | Yes | isTamed, ownerUniqueId, owner |
| `Entity` | `cgk` (Entity) | Yes | UUID, type, location, world, teleport, remove, metadata |

### Implemented Events (50+)

All events fire from ASM-injected bytecode hooks and support cancellation where appropriate.

**Player Events:**
- `PlayerJoinEvent` — fires at end of PlayerList.placeNewPlayer()
- `PlayerQuitEvent` — fires at start of PlayerList.remove()
- `PlayerChatEvent` — cancellable, fires on chat packet
- `PlayerCommandEvent` — cancellable, fires on command packet
- `PlayerMoveEvent` — cancellable, fires on move packet (filtered for meaningful position changes)
- `PlayerDeathEvent` — fires on ServerPlayer.die(), includes death message
- `PlayerRespawnEvent` — fires after respawn completes
- `PlayerTeleportEvent` — fires on ServerPlayer.teleportTo()
- `PlayerDropItemEvent` — cancellable, fires on ServerPlayer.drop()
- `PlayerBucketEvent` — cancellable, fires on BucketItem.use()
- `PlayerInteractEvent` — cancellable, fires on left-click and right-click block
- `PlayerInteractEntityEvent` — cancellable, fires on right-click entity
- `PlayerItemHeldEvent` — cancellable, fires on hotbar slot change

**Block Events:**
- `BlockBreakEvent` — cancellable, fires on ServerPlayerGameMode.destroyBlock()
- `BlockPlaceEvent` — cancellable, fires on BlockItem.place()
- `BlockFromToEvent` — cancellable, fires on liquid flow
- `BlockFormEvent` — cancellable, fires on liquid interactions forming blocks
- `BlockBurnEvent` — cancellable, fires on FireBlock.checkBurnOut()
- `BlockIgniteEvent` — cancellable, fires on fire spread
- `BlockSpreadEvent` — cancellable, fires alongside fire tick
- `BlockPistonEvent` — cancellable, fires on piston extend/retract
- `BlockDispenseEvent` — cancellable, fires on dispenser activation
- `BlockExplodeEvent` — cancellable, fires on explosion without source entity
- `SignChangeEvent` — cancellable, fires on sign update packet

**Entity Events:**
- `EntitySpawnEvent` — cancellable, fires on ServerLevel.addFreshEntity()
- `EntityDamageEvent` — cancellable, fires on LivingEntity.hurtServer(), includes damage cause classification
- `EntityDamageByEntityEvent` — cancellable, fires when damage source is an entity
- `EntityDeathEvent` — fires on LivingEntity.die() (non-player entities)
- `EntityExplodeEvent` — cancellable, fires on explosion with source entity
- `EntityTargetEvent` — cancellable, fires on Mob.setTarget()
- `EntityPickupItemEvent` — cancellable, fires on ServerPlayer.onItemPickup()
- `EntityChangeBlockEvent` — fires for each block changed by explosions
- `EntityInteractEvent` — cancellable, derived from player-entity interaction
- `ItemSpawnEvent` — cancellable, fires when ItemEntity spawns
- `HangingPlaceEvent` — cancellable, fires when hanging entity (painting/item frame) spawns
- `HangingBreakEvent` — cancellable, fires when hanging entity takes damage
- `VehicleDamageEvent` — cancellable, fires when vehicle entity takes damage
- `PotionSplashEvent` — fires on splash potion hit
- `StructureGrowEvent` — cancellable, fires on tree growth

### Server & Infrastructure

| Feature | Status | Details |
|---|---|---|
| `AlloyAPI.server()` | Fully implemented | Real MinecraftServer wrapper (upgrades from stub at startup) |
| `server.onlinePlayers()` | Fully implemented | Live player list from PlayerList |
| `server.player(UUID/String)` | Fully implemented | Lookup via PlayerList |
| `server.worlds()` | Fully implemented | All loaded ServerLevels |
| `server.broadcast()` | Fully implemented | Sends to all online players |
| `server.currentTick()` | Fully implemented | From MinecraftServer tick count |
| `AlloyAPI.scheduler()` | Fully implemented | Tick-synchronized (sync tasks run on server thread) |
| `AlloyAPI.eventBus()` | Fully implemented | Priority-based, cancellation support, thread-safe |
| `AlloyAPI.commandRegistry()` | Fully implemented | Intercepts vanilla command dispatch |
| `AlloyAPI.permissionRegistry()` | Fully implemented | File-based default + pluggable providers |
| `AlloyAPI.economy()` | Fully implemented | File-based default + pluggable providers, balances/transfers/deposits |
| Metadata system | Fully implemented | Per-entity `ConcurrentHashMap<UUID, Map<String, Object>>` |

### Known Limitations

- **Cross-dimension entity teleport**: `Entity.teleport()` (non-player) uses `moveTo()` which works within the same world. Cross-dimension teleport for non-player entities requires `changeDimension()` which is not yet implemented. `Player.teleport()` supports cross-dimension via `ServerPlayer.teleportTo(ServerLevel, ...)`.
- **Player.targetBlock()**: Ray-tracing not yet implemented — returns null.
- **Player.sendBlockChange()**: Fully implemented — constructs and sends `ClientboundBlockUpdatePacket` to the player.
- **LivingEntity.setTarget()**: Requires Mob-specific method — no-op for non-Mob entities.
- **Player.firstPlayed()**: Returns 0 (would require persistent storage across sessions).
- **onDisable lifecycle**: No mod shutdown hook yet — only `onInitialize()` is available.
- **Networking API**: Custom packet registration not yet available.
- **Registry API**: Block/item/entity registration not yet available (mods can use events but not add new content types).

---

## Converting from Bukkit/Spigot/Paper

### Step-by-Step Process

1. **Create `alloy.mod.json`** — replace `plugin.yml`
2. **Replace entry point** — `JavaPlugin` → `ModInitializer`
3. **Replace event system** — Bukkit listeners → Alloy listeners
4. **Replace commands** — Bukkit `CommandExecutor` → Alloy `Command`
5. **Replace permissions** — `plugin.yml` permissions → `PermissionRegistry`
6. **Replace scheduler** — `BukkitScheduler` → Alloy `Scheduler`
7. **Replace config** — `FileConfiguration` → Gson/manual JSON
8. **Replace server access** — `Bukkit.getServer()` → `AlloyAPI.server()`

### Entry Point Conversion

**Bukkit:**
```java
public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Plugin enabled!");
        getServer().getPluginManager().registerEvents(new MyListener(), this);
        getCommand("heal").setExecutor(new HealCommand());
    }

    @Override
    public void onDisable() {
        getLogger().info("Plugin disabled!");
    }
}
```

**Alloy:**
```java
public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println("[MyMod] Mod initialized!");
        AlloyAPI.eventBus().register(new MyListener());
        AlloyAPI.commandRegistry().register(new HealCommand());
    }
}
```

### Event Conversion

**Bukkit:**
```java
public class MyListener implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage("Welcome!");
    }
}
```

**Alloy:**
```java
public class MyListener implements net.alloymc.api.event.Listener {
    @net.alloymc.api.event.EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        // Use Alloy's event types
    }
}
```

### Command Conversion

**Bukkit:**
```java
public class HealCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("myplugin.heal")) {
            sender.sendMessage("No permission!");
            return true;
        }
        sender.sendMessage("Healed!");
        return true;
    }
}
```

**Alloy:**
```java
public class HealCommand extends net.alloymc.api.command.Command {
    public HealCommand() {
        super("heal", "Heal a player", "myplugin.heal", List.of("h"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        sender.sendMessage("Healed!");
        return true;
    }
}
```

Note: Permission checking can be done in the command itself via `sender.hasPermission()`, or integrated with the permission system at a higher level.

### Scheduler Conversion

**Bukkit:**
```java
Bukkit.getScheduler().runTaskLater(plugin, () -> { /* ... */ }, 20L);
Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> { /* ... */ }, 0L, 100L);
```

**Alloy:**
```java
AlloyAPI.scheduler().runTaskLater(() -> { /* ... */ }, 20);
AlloyAPI.scheduler().runAsyncTimer(() -> { /* ... */ }, 0, 100);
```

Key difference: Alloy scheduler methods don't require a plugin instance parameter.

### Permission Registration

**Bukkit (plugin.yml):**
```yaml
permissions:
  myplugin.heal:
    description: Allow healing
    default: op
  myplugin.chat:
    description: Chat features
    default: true
```

**Alloy (in onInitialize):**
```java
PermissionRegistry registry = AlloyAPI.permissionRegistry();
registry.register("myplugin.heal", "Allow healing", PermissionDefault.OP);
registry.register("myplugin.chat", "Chat features", PermissionDefault.TRUE);
```

---

## Converting from Fabric

### Step-by-Step Process

1. **Create `alloy.mod.json`** — replace `fabric.mod.json`
2. **Replace entry point** — Fabric `ModInitializer` → Alloy `ModInitializer`
3. **Replace events** — Fabric callbacks → Alloy event bus
4. **Replace registry access** — Fabric `Registry` → Alloy registries
5. **Remove mixin usage** — Alloy handles bytecode injection internally
6. **Replace networking** — Fabric networking → Alloy networking (future)

### Entry Point Conversion

**Fabric (`fabric.mod.json`):**
```json
{
  "schemaVersion": 1,
  "id": "mymod",
  "version": "1.0.0",
  "entrypoints": {
    "main": ["com.example.MyMod"],
    "client": ["com.example.MyModClient"]
  },
  "depends": {
    "fabricloader": ">=0.15.0",
    "minecraft": "~1.21"
  }
}
```

**Alloy (`alloy.mod.json`):**
```json
{
  "id": "mymod",
  "name": "My Mod",
  "version": "1.0.0",
  "entrypoint": "com.example.MyMod",
  "dependencies": {
    "alloy": ">=0.1.0",
    "minecraft": ">=1.21.0"
  },
  "environment": "both"
}
```

Key differences:
- Single entrypoint (use `AlloyAPI.environment()` to branch client/server logic)
- No schema version
- Simpler dependency syntax

**Fabric:**
```java
public class MyMod implements net.fabricmc.api.ModInitializer {
    @Override
    public void onInitialize() {
        // ...
    }
}
```

**Alloy:**
```java
public class MyMod implements net.alloymc.loader.api.ModInitializer {
    @Override
    public void onInitialize() {
        // Same lifecycle concept, different package
    }
}
```

### Fabric Event Callbacks → Alloy Events

**Fabric:**
```java
ServerTickEvents.END_SERVER_TICK.register(server -> {
    // tick logic
});

ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
    // player joined
});
```

**Alloy:**
```java
AlloyAPI.eventBus().register(new Listener() {
    @EventHandler
    public void onTick(ServerTickEvent event) {
        // tick logic
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // player joined
    }
});
```

### Removing Mixins

Fabric mods often use Mixins to inject into Minecraft internals. In Alloy, this is **not needed** because:

1. The Alloy API provides stable abstractions over Minecraft internals
2. The Alloy loader handles bytecode injection internally
3. If you need functionality not in the API, request it as an API addition

**Fabric (Mixin):**
```java
@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(CallbackInfo ci) {
        // Custom rendering
    }
}
```

**Alloy:** Use events or the HUD layer system instead:
```java
// For UI additions, use HUD layers or events
// No direct Minecraft class access needed
```

---

## Converting from Forge

### Step-by-Step Process

1. **Create `alloy.mod.json`** — replace `mods.toml` + `@Mod` annotation
2. **Replace entry point** — `@Mod` class → `ModInitializer`
3. **Replace events** — Forge event bus → Alloy event bus
4. **Replace registries** — `DeferredRegister` → Alloy registries
5. **Replace capabilities** — Forge caps → Alloy API patterns
6. **Replace config** — Forge config → Gson/manual JSON
7. **Remove `@Mod` annotations** — Alloy uses `alloy.mod.json` instead

### Entry Point Conversion

**Forge:**
```java
@Mod("mymod")
public class MyMod {
    public MyMod() {
        MinecraftForge.EVENT_BUS.register(this);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    }

    private void setup(final FMLCommonSetupEvent event) {
        // ...
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // ...
    }
}
```

**Alloy:**
```java
public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        AlloyAPI.eventBus().register(new MyListener());
    }
}

public class MyListener implements Listener {
    @EventHandler
    public void onServerTick(ServerTickEvent event) {
        // ...
    }
}
```

### Forge Config → JSON

**Forge:**
```java
ForgeConfigSpec.IntValue maxHealth;
ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
maxHealth = builder.defineInRange("maxHealth", 20, 1, 100);
```

**Alloy (using Gson):**
```java
public record MyConfig(int maxHealth) {
    public static MyConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            MyConfig defaults = new MyConfig(20);
            Files.writeString(file, new Gson().toJson(defaults));
            return defaults;
        }
        return new Gson().fromJson(Files.readString(file), MyConfig.class);
    }
}
```

---

## Mapping Tables

### Bukkit → Alloy

| Bukkit | Alloy |
|--------|-------|
| `JavaPlugin` | `ModInitializer` |
| `plugin.yml` | `alloy.mod.json` |
| `Bukkit.getServer()` | `AlloyAPI.server()` |
| `PluginManager.registerEvents()` | `AlloyAPI.eventBus().register()` |
| `@EventHandler` (Bukkit) | `@EventHandler` (Alloy) |
| `EventPriority` (Bukkit) | `EventPriority` (Alloy) |
| `CommandExecutor` | `Command` (extend abstract class) |
| `plugin.yml` commands | `CommandRegistry.register()` |
| `plugin.yml` permissions | `PermissionRegistry.register()` |
| `BukkitScheduler.runTask()` | `AlloyAPI.scheduler().runTask()` |
| `BukkitScheduler.runTaskLater()` | `AlloyAPI.scheduler().runTaskLater()` |
| `BukkitScheduler.runTaskTimer()` | `AlloyAPI.scheduler().runTaskTimer()` |
| `BukkitScheduler.runTaskAsync*()` | `AlloyAPI.scheduler().runAsync*()` |
| `FileConfiguration` | Gson + custom records |
| `Player.hasPermission()` | `PermissionHolder.hasPermission()` |
| `Player.isOp()` | `PermissionHolder.isOp()` |
| `Bukkit.broadcastMessage()` | `AlloyAPI.server().broadcast()` |
| `Bukkit.getOnlinePlayers()` | `AlloyAPI.server().onlinePlayers()` |
| `Server.getWorlds()` | `AlloyAPI.server().worlds()` |
| `onEnable()` | `onInitialize()` |
| `onDisable()` | *(not yet available — coming in future API)* |
| Vault `Economy` | `EconomyProvider` (implement interface) |
| `economy.getBalance()` | `AlloyAPI.economy().provider().getBalance()` |
| `economy.depositPlayer()` | `AlloyAPI.economy().provider().deposit()` |
| `economy.withdrawPlayer()` | `AlloyAPI.economy().provider().withdraw()` |
| Vault `RegisteredServiceProvider` | `AlloyAPI.economy().setProvider()` |

### Fabric → Alloy

| Fabric | Alloy |
|--------|-------|
| `fabric.mod.json` | `alloy.mod.json` |
| `net.fabricmc.api.ModInitializer` | `net.alloymc.loader.api.ModInitializer` |
| `net.fabricmc.api.ClientModInitializer` | `ModInitializer` + check `AlloyAPI.environment()` |
| `net.fabricmc.api.DedicatedServerModInitializer` | `ModInitializer` + check `AlloyAPI.environment()` |
| Fabric event callbacks (`*.register()`) | `AlloyAPI.eventBus().register()` + `@EventHandler` |
| `@Environment(EnvType.CLIENT)` | `"environment": "client"` in `alloy.mod.json` |
| `FabricLoader.getInstance()` | `AlloyLoader.getInstance()` |
| `FabricLoader.getModContainer()` | `AlloyLoader.getInstance().getLoadedMods()` |
| Mixin `@Inject`, `@Redirect`, etc. | Not needed — use Alloy API events/hooks |
| `fabric.mod.json` `depends` | `alloy.mod.json` `dependencies` |
| `ServerTickEvents.END_SERVER_TICK` | `@EventHandler` on tick event |

### Forge → Alloy

| Forge | Alloy |
|-------|-------|
| `@Mod("modid")` | `alloy.mod.json` `"id"` field |
| `mods.toml` | `alloy.mod.json` |
| `MinecraftForge.EVENT_BUS` | `AlloyAPI.eventBus()` |
| `@SubscribeEvent` | `@EventHandler` |
| `FMLCommonSetupEvent` | `onInitialize()` |
| `FMLClientSetupEvent` | `onInitialize()` + environment check |
| `DeferredRegister` | Direct registry calls in `onInitialize()` |
| `ForgeConfigSpec` | Gson + custom records |
| `ICapabilityProvider` | Alloy API interfaces |
| `NetworkChannel` | Alloy networking (future) |

---

## Common Pitfalls

### 1. Importing Minecraft Classes Directly

**Wrong:**
```java
import net.minecraft.world.level.Level;  // DON'T DO THIS
```

**Right:**
```java
import net.alloymc.api.world.World;  // Use the Alloy API
```

Alloy's API is a firewall. When Minecraft updates and renames `Level` to something else, mods using the API are unaffected.

### 2. Using Bukkit/Fabric/Forge APIs

Remove ALL imports from:
- `org.bukkit.*`
- `net.fabricmc.*`
- `net.minecraftforge.*`
- `net.neoforged.*`
- `org.spongepowered.asm.*` (Mixin)

Replace with `net.alloymc.api.*` and `net.alloymc.loader.api.*`.

### 3. Missing `alloy.mod.json`

Every mod JAR must have `alloy.mod.json` at the **root** of the JAR (not in a subdirectory). Without it, the loader silently skips the JAR.

### 4. Invalid Mod ID

Mod IDs must be lowercase alphanumeric with hyphens: `my-cool-mod`. No underscores, no uppercase, no special characters.

### 5. Wrong Environment Value

Only three valid values: `"client"`, `"server"`, `"both"`. Anything else causes a parse error.

### 6. Static State Across Client/Server

If your mod sets `environment: "both"`, be careful with static fields. On an integrated server (singleplayer), both client and server code run in the same JVM.

### 7. Blocking the Main Thread

Never do I/O, HTTP calls, or database queries on the main thread. Use `AlloyAPI.scheduler().runAsync()` for blocking work.

### 8. Not Registering Permissions

If you check `sender.hasPermission("mymod.something")` but never register the node, the permission system won't know the default value. Always register permissions in `onInitialize()`.

### 9. Circular Dependencies

If mod A depends on mod B and mod B depends on mod A, the dependency resolver will throw a clear error. Structure your mods to avoid circular references — use a shared API module if needed.

### 10. Forgetting `environment` in `alloy.mod.json`

Default is `"both"`, which is correct for most mods. But if your mod uses client-only classes (rendering, GUI), set `"environment": "client"` or the server will crash trying to load those classes.

---

## Build & Test Workflow

### Project Setup

1. **Clone the Alloy repo:**
   ```bash
   git clone <alloy-repo-url>
   cd alloy
   ```

2. **Set up workspace** (downloads Minecraft, generates launch scripts):
   ```bash
   ./gradlew setupWorkspace
   ```

3. **Build everything:**
   ```bash
   ./gradlew build
   ```

4. **Launch client:**
   ```bash
   ./gradlew launchClient
   ```

5. **Launch server:**
   ```bash
   ./gradlew launchServer
   ```

### Developing Your Mod

#### Option A: In-tree (during development)

Add your mod as a Gradle subproject:

1. Create directory: `my-mod/`
2. Add `my-mod/build.gradle.kts`:
   ```kotlin
   plugins {
       java
   }

   dependencies {
       implementation(project(":alloy-api"))
       implementation(project(":alloy-loader"))
   }
   ```
3. Add to `settings.gradle.kts`:
   ```kotlin
   include("my-mod")
   ```
4. Create source files under `my-mod/src/main/java/`
5. Create `my-mod/src/main/resources/alloy.mod.json`

#### Option B: External JAR

1. Build your mod JAR independently
2. Ensure it contains `alloy.mod.json` at the JAR root
3. Ensure `alloy-api` is on the compile classpath
4. Place the built JAR in `run/mods/` (client) or `run-server/mods/` (server)
5. Launch with `./gradlew launchClient` or `./gradlew launchServer`

### Testing Checklist

- [ ] `./gradlew build` passes with no errors
- [ ] `alloy.mod.json` is valid (correct ID format, valid version, valid environment)
- [ ] All dependencies are declared in `alloy.mod.json`
- [ ] Permissions are registered in `onInitialize()`
- [ ] Commands are registered in `onInitialize()`
- [ ] No direct Minecraft class imports
- [ ] No Bukkit/Fabric/Forge imports remaining
- [ ] Client-only code guarded by environment check or `"environment": "client"`
- [ ] Async work uses `scheduler().runAsync*()`, not raw threads
- [ ] Mod loads correctly on client: `./gradlew launchClient`
- [ ] Mod loads correctly on server: `./gradlew launchServer`
- [ ] Mod appears in `/mods` command output
- [ ] Commands work and permissions are enforced

### Gradle Commands Reference

| Command | Description |
|---------|-------------|
| `./gradlew build` | Build all modules |
| `./gradlew build --no-build-cache` | Clean build (no caching) |
| `./gradlew setupWorkspace` | Download Minecraft, generate launch scripts |
| `./gradlew launchClient` | Launch Minecraft client with Alloy |
| `./gradlew launchServer` | Launch dedicated server with Alloy |
| `./gradlew :alloy-core:jar` | Build just alloy-core |
| `./gradlew :my-mod:jar` | Build just your mod |
| `./gradlew test` | Run all tests |
| `./gradlew clean` | Clean all build outputs |

---

## Quick Reference Card

### Minimal Mod Template

**`alloy.mod.json`:**
```json
{
  "id": "my-mod",
  "name": "My Mod",
  "version": "1.0.0",
  "entrypoint": "com.example.MyMod",
  "dependencies": { "alloy": ">=0.1.0" },
  "environment": "both"
}
```

**`MyMod.java`:**
```java
package com.example;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.permission.PermissionRegistry.PermissionDefault;
import net.alloymc.loader.api.ModInitializer;

public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Register permissions
        AlloyAPI.permissionRegistry().register(
            "mymod.greet", "Greet command", PermissionDefault.TRUE);

        // Register commands
        AlloyAPI.commandRegistry().register(new GreetCommand());

        // Register event listeners
        AlloyAPI.eventBus().register(new MyListener());

        System.out.println("[MyMod] Ready!");
    }
}
```

**`build.gradle.kts`:**
```kotlin
plugins { java }

dependencies {
    implementation(project(":alloy-api"))
    implementation(project(":alloy-loader"))
}

tasks.jar {
    archiveBaseName.set("MyMod")
}
```

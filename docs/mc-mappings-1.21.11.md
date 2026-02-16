# Minecraft 1.21.11 Obfuscated Mappings Reference

> **Source:** `cache/1.21.11/client_mappings.txt` (Mojang official ProGuard mappings)
>
> **Last verified:** 2026-02-16
>
> This document lists every obfuscated class, method, and field name used by Alloy's
> reflection-based runtime wrappers. All mappings have been verified against the official
> Mojang ProGuard mappings file for MC 1.21.11.

---

## Class Mappings

| Deobfuscated Name | Obfuscated | Used In |
|---|---|---|
| `net.minecraft.server.MinecraftServer` | `net.minecraft.server.MinecraftServer` (not obfuscated) | ReflectiveServer |
| `net.minecraft.server.level.ServerPlayer` | `axg` | ReflectivePlayer, EventFiringHook |
| `net.minecraft.server.level.ServerLevel` | `axf` | ReflectiveWorld, EventFiringHook |
| `net.minecraft.server.level.ServerPlayerGameMode` | `axh` | EventFiringHook, ReflectivePlayer |
| `net.minecraft.server.players.PlayerList` | `bbz` | ReflectiveServer |
| `net.minecraft.server.network.ServerGamePacketListenerImpl` | `ayi` | EventFiringHook, AlloyTransformer |
| `net.minecraft.server.network.ServerCommonPacketListenerImpl` | `ayf` | ReflectivePlayer (send/disconnect) |
| `net.minecraft.world.entity.Entity` | `cgk` | ReflectiveEntity, EntityWrapperFactory |
| `net.minecraft.world.entity.LivingEntity` | `chl` | ReflectiveLivingEntity, EntityWrapperFactory |
| `net.minecraft.world.entity.projectile.Projectile` | `dec` | EntityWrapperFactory |
| `net.minecraft.world.entity.TamableAnimal` | `cii` | EntityWrapperFactory |
| `net.minecraft.world.entity.player.Inventory` | `ddl` | ReflectiveInventory |
| `net.minecraft.world.level.Level` | `dwo` | ReflectiveWorld, ReflectiveBlock |
| `net.minecraft.world.level.LevelHeightAccessor` | `dwq` | ReflectiveWorld (inherited by Level) |
| `net.minecraft.world.level.block.Block` | `dzq` | ReflectiveBlock, sendBlockChange |
| `net.minecraft.world.level.block.state.BlockState` | `eoh` | (used as return type) |
| `net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase` | `eog$a` | ReflectiveBlock (isAir, getBlock) |
| `net.minecraft.world.item.ItemStack` | `dlt` | ReflectiveItemStack |
| `net.minecraft.core.BlockPos` | `is` | EventFiringHook, ReflectiveBlock |
| `net.minecraft.core.registries.BuiltInRegistries` | `mi` | sendBlockChange, setType |
| `net.minecraft.resources.Identifier` | `amo` | sendBlockChange, setType |
| `net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket` | `adj` | sendBlockChange |
| `net.minecraft.network.chat.Component` | `yh` | sendMessage, kick, broadcast |

---

## MinecraftServer Methods

> Class: `net.minecraft.server.MinecraftServer` (not obfuscated)

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getPlayerList()` | `aj()` | PlayerList (bbz) | |
| `getAllLevels()` | `P()` | Iterable\<ServerLevel\> | |
| `getTickCount()` | `am()` | int | |
| `overworld()` | `N()` | ServerLevel (axf) | |
| `getPort()` | `V()` | int | |
| `isSingleplayer()` | `X()` | boolean | |
| `getCommands()` | `aF()` | Commands | For Brigadier dispatcher |
| `sendSystemMessage(Component)` | `a(Component)` | void | |

---

## ServerPlayer Methods & Fields

> Class: `axg` extends Player (`ddj`) extends LivingEntity (`chl`) extends Entity (`cgk`)

| Method/Field | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getGameProfile()` | `gI()` | GameProfile | `.name()` and `.id()` for name/UUID |
| `connection` (field) | `g` | ServerGamePacketListenerImpl (ayi) | |
| `getInventory()` | `gK()` | Inventory (ddl) | |
| `level()` (as ServerLevel) | `A()` | ServerLevel (axf) | ServerPlayer-specific override |
| `experienceLevel` (field) | `cs` | int | |
| `sendSystemMessage(Component)` | `a(Component)` | void | |
| `sendSystemMessage(Component, boolean)` | `b(Component, boolean)` | void | |

---

## Entity Methods & Fields

> Class: `cgk`

| Method/Field | Obfuscated | Returns | Notes |
|---|---|---|---|
| `position()` | `dI()` | Vec3 | Vec3 fields: x=`g`, y=`h`, z=`i` |
| `level()` | `ao()` | Level (dwo) | Generic Level return type |
| `getUUID()` | `cY()` | UUID | |
| `isAlive()` | `cb()` | boolean | |
| `isShiftKeyDown()` | `cu()` | boolean | |
| `getYRot()` | `ec()` | float | Yaw |
| `getXRot()` | `ee()` | float | Pitch |
| `getType()` | `ay()` | EntityType | |
| `getName()` | — | Component | (check method signature) |
| `isSpectator()` | `au()` | boolean | |

---

## LivingEntity Methods

> Class: `chl` extends Entity (`cgk`)

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getHealth()` | `eZ()` | float | |
| `getMaxHealth()` | `fq()` | float | |
| `setHealth(float)` | `x(float)` | void | |
| `isDeadOrDying()` | `fa()` | boolean | |
| `hurt(DamageSource, float)` | `a(DamageSource, float)` | boolean | Via signature matching |
| `getMainHandItem()` | `fx()` | ItemStack (dlt) | |
| `getScale()` | `eF()` | float | |
| `isBaby()` | `e_()` | boolean | |

---

## Level / World Methods

> Class: `dwo` implements LevelHeightAccessor (`dwq`)

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getBlockState(BlockPos)` | `a_(BlockPos)` | BlockState (eoh) | |
| `setBlock(BlockPos, BlockState, int)` | `a(BlockPos, BlockState, int)` | boolean | flags: 3 = UPDATE_ALL |
| `dimension()` | `aq()` | ResourceKey | `.a()` → Identifier → `.toString()` for name |
| `getSeaLevel()` | `V()` | int | |
| `isClientSide()` | `B_()` | boolean | |
| `getServer()` | `s()` | MinecraftServer | |

### LevelHeightAccessor (interface `dwq`, inherited by Level)

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getMinY()` | `K_()` | int | Typically -64 for overworld |
| `getHeight()` | `L_()` | int | Height range (384 for overworld) |
| `getMaxY()` | `aw()` | int | = minY + height (320 for overworld) |

---

## ServerLevel Methods

> Class: `axf` extends Level (`dwo`)

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getAllEntities()` | `H()` | Iterable\<Entity\> | |
| `players()` | `E()` | List\<ServerPlayer\> | |
| `isPvpAllowed()` | `X()` | boolean | |

---

## PlayerList Methods

> Class: `bbz`

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getPlayers()` | `t()` | List\<ServerPlayer\> | |
| `getPlayer(UUID)` | `b(UUID)` | ServerPlayer (nullable) | |
| `getPlayerByName(String)` | `a(String)` | ServerPlayer (nullable) | |
| `broadcastSystemMessage(Component, boolean)` | `a(Component, boolean)` | void | |
| `broadcastAll(Packet)` | `a(Packet)` | void | |

---

## BlockStateBase Methods

> Class: `eog$a` (inner class of BlockBehaviour)

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getBlock()` | `b()` | Block (dzq) | |
| `isAir()` | `l()` | boolean | |
| `liquid()` | `n()` | boolean | |
| `isSolid()` | `e()` | boolean | Actually `isSolid` → `e()` |
| `blocksMotion()` | `d()` | boolean | |
| `getLightBlock()` | `g()` | int | NOT isAir! |
| `canOcclude()` | `t()` | boolean | |

### Important: Fields vs Methods on BlockStateBase

| Field | Obfuscated | Type | Notes |
|---|---|---|---|
| `isAir` (field) | `j` | boolean | Direct field, NOT the method |
| `liquid` (field) | `l` | boolean | Field name matches isAir METHOD name |
| `legacySolid` (field) | `m` | boolean | |

> **Warning:** Field `l` = `liquid` but method `l()` = `isAir()`. Always use the method, not the field.

---

## Block Methods

> Class: `dzq`

| Method/Field | Obfuscated | Returns | Notes |
|---|---|---|---|
| `defaultBlockState()` | `m()` | BlockState (eoh) | METHOD, not field |
| `defaultBlockState` (field) | `d` | BlockState | Direct field access |
| `getName()` | `f()` | MutableComponent | |
| `getExplosionResistance()` | `e()` | float | |
| `getFriction()` | `g()` | float | |

---

## ItemStack Methods

> Class: `dlt`

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getItem()` | `h()` | Item | |
| `getCount()` | `N()` | int | |
| `isEmpty()` | `f()` | boolean | |
| `copy()` | `v()` | ItemStack | |
| `setCount(int)` | `e(int)` | void | |
| `getMaxStackSize()` | `k()` | int | |
| `isDamaged()` | `n()` | boolean | |
| `getDamageValue()` | `o()` | int | |
| `getMaxDamage()` | `p()` | int | |

---

## Inventory Methods

> Class: `ddl` (net.minecraft.world.entity.player.Inventory)

| Method/Field | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getContainerSize()` | `b()` | int | |
| `getItem(int)` | `a(int)` | ItemStack | |
| `isEmpty()` | `c()` | boolean | |
| `clearContent()` | `a()` | void | |
| `selected` (field) | `m` | int | Current hotbar slot |
| `getSelectedItem()` | `h()` | ItemStack | |
| `player` (field) | `j` | Player | |

---

## BlockPos Methods

> Class: `is` extends Vec3i

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| constructor | `new is(int, int, int)` | BlockPos | |
| `getX()` | `u()` | int | Inherited from Vec3i |
| `getY()` | `v()` | int | Inherited from Vec3i |
| `getZ()` | `w()` | int | Inherited from Vec3i |
| `asLong()` | `a()` | long | Packed representation |
| `offset(int, int, int)` | `b(int, int, int)` | BlockPos | |

---

## Identifier (formerly ResourceLocation) Methods

> Class: `amo`

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `parse(String)` | `a(String)` | Identifier | Static method |
| `fromNamespaceAndPath(String, String)` | `a(String, String)` | Identifier | Static |
| `withDefaultNamespace(String)` | `b(String)` | Identifier | Static |
| `getPath()` | `a()` | String | |
| `getNamespace()` | `b()` | String | |
| `toString()` | `toString()` | String | Not obfuscated |

---

## BuiltInRegistries Fields

> Class: `mi`

| Field | Obfuscated | Type | Notes |
|---|---|---|---|
| `BLOCK` | `e` | Registry\<Block\> | Block registry |

> Registry lookup: `registry.a(Identifier)` → returns registered object (e.g., Block)

---

## ClientboundBlockUpdatePacket

> Class: `adj`

| Constructor | Notes |
|---|---|
| `adj(BlockPos, BlockState)` | Use `getDeclaredConstructors()` and match by param types |

---

## Component (Chat) Methods

> Class/Interface: `yh`

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `literal(String)` | `b(String)` | MutableComponent | **Static** — NOT `a()`! |
| `nullToEmpty(String)` | `a(String)` | Component | Static, returns EMPTY for null |
| `translatable(String)` | `c(String)` | MutableComponent | Static |
| `empty()` | `i()` | MutableComponent | Static |
| `getString()` | `getString()` | String | Not obfuscated |

---

## ServerCommonPacketListenerImpl Methods

> Class: `ayf`

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `send(Packet)` | `b(Packet)` | void | Send network packet to client |
| `disconnect(Component)` | `a(Component)` | void | Kick player with message |

---

## ServerGamePacketListenerImpl Fields

> Class: `ayi` extends `ayf`

| Field | Obfuscated | Type | Notes |
|---|---|---|---|
| `player` | `g` | ServerPlayer (axg) | Also `connection` field on ServerPlayer |

---

## ServerPlayerGameMode Fields

> Class: `axh`

| Field | Obfuscated | Type | Notes |
|---|---|---|---|
| `player` | `d` | ServerPlayer (axg) | |
| `level` | `c` | ServerLevel (axf) | |

| Method | Obfuscated | Returns | Notes |
|---|---|---|---|
| `getGameModeForPlayer()` | `b()` | GameType | Enum: SURVIVAL=0, CREATIVE=1, ADVENTURE=2, SPECTATOR=3 |

---

## Entity Type Hierarchy (for EntityWrapperFactory)

| MC Class | Obfuscated | Alloy Wrapper |
|---|---|---|
| ServerPlayer | `axg` | ReflectivePlayer |
| LivingEntity | `chl` | ReflectiveLivingEntity |
| Projectile | `dec` | ReflectiveProjectile |
| TamableAnimal | `cii` | ReflectiveTameableEntity |
| VehicleEntity | `dga` | (isVehicle check) |
| HangingEntity | `czb` | (isHangingEntity check) |
| Entity | `cgk` | ReflectiveEntity |

---

## How to Look Up New Mappings

The ProGuard mappings file format:

```
net.minecraft.some.package.ClassName -> obf:
    type fieldName -> obfFieldName
    line:line:returnType methodName(paramTypes) -> obfMethodName
```

Search the file at `cache/1.21.11/client_mappings.txt`:

```bash
# Find a class
grep "^net.minecraft.world.entity.LivingEntity " client_mappings.txt

# Find a method (within any class)
grep "getHealth()" client_mappings.txt

# Find what maps to a specific obfuscated name
grep "-> aj$" client_mappings.txt   # (for fields/methods)
grep "-> aj:" client_mappings.txt   # (for classes)
```

> **Important:** Field names and method names can collide. For example, on BlockStateBase,
> field `l` = `liquid` but method `l()` = `isAir()`. Always check the full mapping line
> to distinguish fields from methods.

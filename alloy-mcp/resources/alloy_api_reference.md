# Alloy API Reference

## Core Concepts

Alloy mods never import Minecraft classes directly. All interaction goes through the Alloy API.
If Mojang renames or refactors something, Alloy's mappings + API absorb the change.

## Mod Entry Point

```java
import net.alloymc.api.mod.ModInitializer;
import net.alloymc.api.mod.AlloyMod;

@AlloyMod(id = "mymod", name = "My Mod", version = "1.0.0")
public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        // Register blocks, items, events, etc.
    }
}
```

## Event System

```java
import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.block.BlockBreakEvent;
import net.alloymc.api.event.entity.EntitySpawnEvent;
import net.alloymc.api.event.player.PlayerJoinEvent;

@EventHandler
public void onBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    Block block = event.getBlock();
    // event.setCancelled(true); to prevent
}
```

### Available Events
- **Block:** BlockBreakEvent, BlockPlaceEvent, BlockInteractEvent
- **Entity:** EntitySpawnEvent, EntityDamageEvent, EntityDeathEvent
- **Player:** PlayerJoinEvent, PlayerQuitEvent, PlayerChatEvent, PlayerMoveEvent
- **Inventory:** InventoryOpenEvent, InventoryClickEvent, InventoryCloseEvent
- **World:** WorldLoadEvent, ChunkLoadEvent, ChunkUnloadEvent
- **Server:** ServerTickEvent, ServerStartEvent, ServerStopEvent

## Registry System

```java
import net.alloymc.api.registry.Registry;
import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockProperties;
import net.alloymc.api.item.Item;
import net.alloymc.api.item.ItemProperties;

// Register a block
Block MY_BLOCK = Registry.register(Blocks.class, "my_block",
    new Block(BlockProperties.of().strength(3.0f, 6.0f)));

// Register an item
Item MY_ITEM = Registry.register(Items.class, "my_item",
    new Item(ItemProperties.of().maxStackSize(64)));

// Register a block item
Item MY_BLOCK_ITEM = Registry.register(Items.class, "my_block",
    new BlockItem(MY_BLOCK, new ItemProperties()));
```

## Block API

```java
import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockProperties;
import net.alloymc.api.block.BlockState;
import net.alloymc.api.block.BlockWithEntity;
import net.alloymc.api.block.entity.BlockEntity;

public class MyBlock extends Block {
    public MyBlock() {
        super(BlockProperties.of()
            .strength(hardness, resistance)
            .requiresTool()
            .luminance(state -> 15)
            .nonOpaque()
            .gravity()
            .slipperiness(0.98f)
        );
    }
}
```

### BlockProperties Methods
- `.strength(hardness, resistance)` — Mining hardness and explosion resistance
- `.requiresTool()` — Block only drops with correct tool
- `.luminance(state -> level)` — Light emission (0-15)
- `.nonOpaque()` — Transparent/translucent block
- `.gravity()` — Falls like sand
- `.slipperiness(value)` — Surface slipperiness (0.6 default, 0.98 for ice)

## Item API

```java
import net.alloymc.api.item.Item;
import net.alloymc.api.item.ItemProperties;

public class MyItem extends Item {
    public MyItem() {
        super(ItemProperties.of()
            .maxStackSize(64)
            .fireResistant()
            .rarity(Rarity.RARE)
        );
    }
}
```

## GUI/Screen API (Client)

```java
import net.alloymc.api.gui.Screen;
import net.alloymc.api.gui.DrawContext;
import net.alloymc.api.gui.widget.Button;
import net.alloymc.api.gui.widget.SlotWidget;
import net.alloymc.api.gui.widget.ProgressBar;
import net.alloymc.api.util.Identifier;
```

## Animation API (Client)

```java
import net.alloymc.api.client.animation.Animation;
import net.alloymc.api.client.animation.AnimationTrack;
import net.alloymc.api.client.animation.Keyframe;
import net.alloymc.api.client.animation.Easing;

// Easing types: LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, CUBIC_BEZIER
```

## Networking

```java
import net.alloymc.api.network.PacketRegistry;
import net.alloymc.api.network.CustomPacket;

PacketRegistry.register("mymod:my_packet", MyPacket.class);
```

## Configuration

```java
import net.alloymc.api.config.Config;
import net.alloymc.api.config.ConfigEntry;

@Config(id = "mymod")
public class MyModConfig {
    @ConfigEntry(comment = "Enable feature X")
    public boolean enableFeatureX = true;

    @ConfigEntry(min = 0, max = 100)
    public int maxItems = 64;
}
```

## Environment Annotations

```java
import net.alloymc.api.Environment;
import net.alloymc.api.EnvType;

@Environment(EnvType.CLIENT)  // Only available on client
@Environment(EnvType.SERVER)  // Only available on server
```

## Mod Manifest (alloy.mod.json)

```json
{
    "id": "mymod",
    "name": "My Mod",
    "version": "1.0.0",
    "environment": "both",
    "entry_point": "com.mymod.MyMod",
    "dependencies": {
        "alloy": ">=0.1.0"
    },
    "description": "A cool mod",
    "authors": ["Developer"],
    "license": "MIT"
}
```

## Modpack Manifest (alloy.pack.toml)

```toml
[pack]
name = "My Modpack"
version = "1.0.0"
minecraft_version = "1.21.4"
alloy_version = "0.1.0"

[[mods]]
id = "mymod"
name = "My Mod"
version = "1.0.0"
environment = "both"
source = "local"
```

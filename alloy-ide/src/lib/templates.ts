/**
 * Alloy mod file templates.
 * Generates boilerplate Java files for common mod patterns.
 */

export interface FileTemplate {
  id: string;
  label: string;
  description: string;
  defaultName: string;
  generate: (className: string, packageName: string) => string;
}

function inferPackage(dirPath: string): string {
  // Try to extract package from src/main/java/... path
  const srcIdx = dirPath.indexOf("/src/main/java/");
  if (srcIdx >= 0) {
    return dirPath
      .slice(srcIdx + "/src/main/java/".length)
      .replace(/\//g, ".");
  }
  return "net.alloymc.mymod";
}

export function getPackageForPath(dirPath: string): string {
  return inferPackage(dirPath);
}

export const ALLOY_TEMPLATES: FileTemplate[] = [
  {
    id: "event_handler",
    label: "Event Handler",
    description: "Listens for Alloy events",
    defaultName: "MyEventHandler.java",
    generate: (className, pkg) => `package ${pkg};

import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.Listener;
import net.alloymc.api.event.block.BlockBreakEvent;

public class ${className} implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        // Handle the event
    }
}
`,
  },
  {
    id: "block",
    label: "Block",
    description: "Custom block registration",
    defaultName: "MyBlock.java",
    generate: (className, pkg) => `package ${pkg};

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockProperties;
import net.alloymc.api.registry.Registry;

public class ${className} extends Block {

    public static final ${className} INSTANCE = new ${className}();

    public ${className}() {
        super(BlockProperties.of()
            .strength(3.0f, 6.0f)
            .requiresTool()
        );
    }

    public static void register() {
        Registry.register(Registry.BLOCKS, "${className.toLowerCase()}", INSTANCE);
    }
}
`,
  },
  {
    id: "item",
    label: "Item",
    description: "Custom item registration",
    defaultName: "MyItem.java",
    generate: (className, pkg) => `package ${pkg};

import net.alloymc.api.item.Item;
import net.alloymc.api.item.ItemProperties;
import net.alloymc.api.registry.Registry;

public class ${className} extends Item {

    public static final ${className} INSTANCE = new ${className}();

    public ${className}() {
        super(ItemProperties.of()
            .maxStackSize(64)
        );
    }

    public static void register() {
        Registry.register(Registry.ITEMS, "${className.toLowerCase()}", INSTANCE);
    }
}
`,
  },
  {
    id: "entity",
    label: "Entity",
    description: "Custom entity type",
    defaultName: "MyEntity.java",
    generate: (className, pkg) => `package ${pkg};

import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.EntityType;
import net.alloymc.api.world.World;

public class ${className} extends Entity {

    public ${className}(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override
    public void tick() {
        super.tick();
        // Entity logic runs every tick (20 TPS)
    }
}
`,
  },
  {
    id: "command",
    label: "Command",
    description: "Chat command handler",
    defaultName: "MyCommand.java",
    generate: (className, pkg) => `package ${pkg};

import net.alloymc.api.command.Command;
import net.alloymc.api.command.CommandContext;
import net.alloymc.api.command.CommandRegistry;

public class ${className} implements Command {

    @Override
    public String getName() {
        return "${className.toLowerCase().replace("command", "")}";
    }

    @Override
    public String getDescription() {
        return "A custom command";
    }

    @Override
    public void execute(CommandContext context) {
        context.sendMessage("Hello from ${className}!");
    }

    public static void register() {
        CommandRegistry.register(new ${className}());
    }
}
`,
  },
  {
    id: "mod_entry",
    label: "Mod Entry Point",
    description: "ModInitializer implementation",
    defaultName: "MyMod.java",
    generate: (className, pkg) => `package ${pkg};

import net.alloymc.api.mod.ModInitializer;
import net.alloymc.api.mod.ModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ${className} implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("${className}");

    @Override
    public void onInitialize(ModInfo mod) {
        LOGGER.info("{} v{} is loading!", mod.getName(), mod.getVersion());

        // Register blocks, items, entities, commands, etc.
    }
}
`,
  },
  {
    id: "config",
    label: "Config",
    description: "Mod configuration class",
    defaultName: "ModConfig.java",
    generate: (className, pkg) => `package ${pkg};

import net.alloymc.api.config.Config;
import net.alloymc.api.config.ConfigEntry;

@Config(name = "${className.toLowerCase().replace("config", "").replace("mod", "")}-config")
public class ${className} {

    @ConfigEntry(comment = "Enable debug logging")
    public boolean debugMode = false;

    @ConfigEntry(comment = "Maximum allowed value")
    public int maxValue = 100;

    @ConfigEntry(comment = "Display name shown in-game")
    public String displayName = "My Mod";
}
`,
  },
  {
    id: "gui_screen",
    label: "GUI Screen",
    description: "Visual GUI editor file (.gui.json)",
    defaultName: "my_screen.gui.json",
    generate: (name) => JSON.stringify(
      {
        name: name.replace(".gui.json", ""),
        width: 176,
        height: 166,
        background_texture: null,
        elements: [],
      },
      null,
      2,
    ),
  },
  {
    id: "animation",
    label: "Animation",
    description: "Animation timeline file (.anim.json)",
    defaultName: "my_animation.anim.json",
    generate: (name) => JSON.stringify(
      {
        name: name.replace(".anim.json", ""),
        duration_ticks: 40,
        tracks: [],
        sprite_sheet: null,
        frame_width: null,
        frame_height: null,
      },
      null,
      2,
    ),
  },
];

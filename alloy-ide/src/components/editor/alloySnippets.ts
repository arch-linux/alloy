/**
 * Alloy API code snippets for CodeMirror 6 autocompletion.
 * Provides Alloy-specific Java code templates.
 */

import type { CompletionContext, Completion, CompletionSource } from "@codemirror/autocomplete";

interface SnippetDef {
  label: string;
  detail: string;
  template: string;
  type: string;
}

const ALLOY_SNIPPETS: SnippetDef[] = [
  {
    label: "alloy-eventhandler",
    detail: "Event handler method",
    type: "method",
    template: `@EventHandler
public void on\${1:Event}(\${2:PlayerJoinEvent} event) {
    \${3:// Handle event}
}`,
  },
  {
    label: "alloy-block",
    detail: "Block registration",
    type: "keyword",
    template: `public static final Block \${1:MY_BLOCK} = Registry.register(
    Blocks.class,
    "\${2:my_block}",
    new Block(BlockProperties.create()
        .hardness(\${3:1.5f})
        .resistance(\${4:6.0f})
        .sound(SoundType.STONE))
);`,
  },
  {
    label: "alloy-item",
    detail: "Item registration",
    type: "keyword",
    template: `public static final Item \${1:MY_ITEM} = Registry.register(
    Items.class,
    "\${2:my_item}",
    new Item(ItemProperties.create()
        .maxStackSize(\${3:64})
        .tab(CreativeTab.MISC))
);`,
  },
  {
    label: "alloy-command",
    detail: "Custom command",
    type: "method",
    template: `@CommandHandler(name = "\${1:mycommand}", permission = "\${2:mod.command.mycommand}")
public void onCommand(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
        sender.sendMessage("This command can only be used by players.");
        return;
    }
    \${3:// Command logic}
}`,
  },
  {
    label: "alloy-gui",
    detail: "GUI Screen class",
    type: "class",
    template: `public class \${1:MyScreen} extends AlloyScreen {
    public \${1:MyScreen}() {
        super("\${2:My Screen}");
    }

    @Override
    protected void init() {
        super.init();
        \${3:// Add widgets}
    }

    @Override
    protected void drawBackground(DrawContext context, float delta) {
        drawDefaultBackground(context, delta);
        \${4:// Draw custom background}
    }

    @Override
    protected void drawForeground(DrawContext context, float delta) {
        \${5:// Draw foreground elements}
    }
}`,
  },
  {
    label: "alloy-modinit",
    detail: "Mod initializer class",
    type: "class",
    template: `public class \${1:MyMod} implements ModInitializer {
    public static final String MOD_ID = "\${2:mymod}";

    @Override
    public void onInitialize() {
        \${3:// Register blocks, items, events}
    }
}`,
  },
  {
    label: "alloy-clientinit",
    detail: "Client mod initializer",
    type: "class",
    template: `public class \${1:MyModClient} implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        \${3:// Register renderers, key bindings, screens}
    }
}`,
  },
  {
    label: "alloy-entity",
    detail: "Custom entity class",
    type: "class",
    template: `public class \${1:MyEntity} extends LivingEntity {
    public \${1:MyEntity}(EntityType type, World world) {
        super(type, world);
    }

    @Override
    protected void initAttributes() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(\${2:20.0});
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(\${3:0.3});
    }

    @Override
    public void tick() {
        super.tick();
        \${4:// Entity logic}
    }
}`,
  },
  {
    label: "alloy-recipe",
    detail: "Shaped crafting recipe",
    type: "keyword",
    template: `ShapedRecipe.create("\${1:my_recipe}")
    .pattern("\${2:ABA}", "\${3:CDC}", "\${4:ABA}")
    .define('A', Items.\${5:STONE})
    .define('B', Items.\${6:IRON_INGOT})
    .define('C', Items.\${7:DIAMOND})
    .define('D', Items.\${8:GOLD_INGOT})
    .result(new ItemStack(\${9:MY_ITEM}, \${10:1}))
    .register();`,
  },
  {
    label: "alloy-config",
    detail: "Mod config class",
    type: "class",
    template: `@Config(modId = "\${1:mymod}")
public class \${2:MyConfig} {
    @Config.Entry(comment = "\${3:Enable feature}")
    public static boolean \${4:enableFeature} = true;

    @Config.Entry(comment = "\${5:Max count}", min = 1, max = 100)
    public static int \${6:maxCount} = 10;
}`,
  },
  {
    label: "alloy-particle",
    detail: "Particle spawning",
    type: "method",
    template: `world.addParticle(
    ParticleTypes.\${1:FLAME},
    pos.getX() + \${2:0.5},
    pos.getY() + \${3:1.0},
    pos.getZ() + \${4:0.5},
    \${5:0.0}, \${6:0.05}, \${7:0.0}
);`,
  },
  {
    label: "alloy-scheduler",
    detail: "Scheduled task",
    type: "method",
    template: `Scheduler.runLater(() -> {
    \${1:// Delayed logic}
}, \${2:20}); // ticks`,
  },
  {
    label: "alloy-keybind",
    detail: "Key binding registration",
    type: "keyword",
    template: `KeyBinding \${1:myKey} = KeyBindings.register(
    "\${2:key.mymod.action}",
    InputUtil.Type.KEYSYM,
    GLFW.GLFW_KEY_\${3:R},
    "\${4:My Mod}"
);`,
  },
];

/** Parse a snippet template into a CodeMirror-friendly string. Replaces ${N:default} with the default text. */
function resolveTemplate(template: string): string {
  // Replace ${N:text} with just text
  return template.replace(/\$\{\d+:([^}]*)}/g, "$1");
}

function alloyCompletions(context: CompletionContext) {
  // Only suggest when typing "alloy" prefix or at beginning of a line
  const word = context.matchBefore(/[\w-]*/);
  if (!word || (word.from === word.to && !context.explicit)) return null;

  const completions: Completion[] = ALLOY_SNIPPETS.map((s) => ({
    label: s.label,
    detail: s.detail,
    type: s.type,
    apply: resolveTemplate(s.template),
    boost: s.label.startsWith("alloy-") ? 1 : 0,
  }));

  return {
    from: word.from,
    options: completions,
    validFor: /^[\w-]*$/,
  };
}

/**
 * Returns the Alloy snippet completion source.
 */
export function alloySnippets(): CompletionSource {
  return alloyCompletions;
}

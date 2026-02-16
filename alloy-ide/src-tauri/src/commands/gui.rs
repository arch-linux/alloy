use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Deserialize)]
pub struct GenerateGuiCodeArgs {
    /// Path to the .gui.json file
    pub gui_path: String,
    /// The mod project root directory
    pub project_path: String,
    /// The mod ID
    pub mod_id: String,
    /// Package name for the generated class
    pub package_name: String,
}

#[derive(Debug, Serialize)]
pub struct GenerateResult {
    pub java_path: String,
    pub java_code: String,
}

#[derive(Debug, Deserialize)]
pub struct GenerateAnimCodeArgs {
    /// Path to the .anim.json file
    pub anim_path: String,
    /// The mod project root directory
    pub project_path: String,
    /// The mod ID
    pub mod_id: String,
    /// Package name for the generated class
    pub package_name: String,
}

// --- GUI Types matching the frontend GuiProject ---

#[derive(Debug, Deserialize)]
struct GuiProject {
    name: String,
    width: u32,
    height: u32,
    background_texture: Option<String>,
    elements: Vec<GuiElement>,
}

#[derive(Debug, Deserialize)]
struct GuiElement {
    id: String,
    #[serde(rename = "type")]
    element_type: String,
    x: i32,
    y: i32,
    width: u32,
    height: u32,
    label: Option<String>,
    properties: serde_json::Value,
}

// --- Animation Types matching the frontend AnimationProject ---

#[derive(Debug, Deserialize)]
struct AnimProject {
    name: String,
    duration_ticks: u32,
    tracks: Vec<AnimTrack>,
    sprite_sheet: Option<String>,
    frame_width: Option<u32>,
    frame_height: Option<u32>,
}

#[derive(Debug, Deserialize)]
struct AnimTrack {
    id: String,
    property: String,
    target_element: String,
    keyframes: Vec<AnimKeyframe>,
}

#[derive(Debug, Deserialize)]
struct AnimKeyframe {
    tick: u32,
    value: f64,
    easing: String,
}

#[tauri::command]
pub async fn generate_gui_code(args: GenerateGuiCodeArgs) -> Result<GenerateResult, String> {
    let gui_path = Path::new(&args.gui_path);
    if !gui_path.exists() {
        return Err("GUI file does not exist".to_string());
    }

    let content = fs::read_to_string(gui_path)
        .map_err(|e| format!("Failed to read GUI file: {}", e))?;

    let project: GuiProject = serde_json::from_str(&content)
        .map_err(|e| format!("Failed to parse GUI file: {}", e))?;

    let class_name = format!("{}Screen", to_pascal_case(&project.name));
    let handler_class = format!("{}ScreenHandler", to_pascal_case(&project.name));
    let code = generate_screen_class(&project, &args.mod_id, &class_name, &handler_class, &args.package_name);

    // Write the Java file
    let java_dir = Path::new(&args.project_path)
        .join("src/main/java")
        .join(args.package_name.replace('.', "/"))
        .join("client/gui");

    fs::create_dir_all(&java_dir)
        .map_err(|e| format!("Failed to create directory: {}", e))?;

    let java_path = java_dir.join(format!("{}.java", class_name));
    fs::write(&java_path, &code)
        .map_err(|e| format!("Failed to write Java file: {}", e))?;

    Ok(GenerateResult {
        java_path: java_path.to_string_lossy().to_string(),
        java_code: code,
    })
}

#[tauri::command]
pub async fn generate_anim_code(args: GenerateAnimCodeArgs) -> Result<GenerateResult, String> {
    let anim_path = Path::new(&args.anim_path);
    if !anim_path.exists() {
        return Err("Animation file does not exist".to_string());
    }

    let content = fs::read_to_string(anim_path)
        .map_err(|e| format!("Failed to read animation file: {}", e))?;

    let project: AnimProject = serde_json::from_str(&content)
        .map_err(|e| format!("Failed to parse animation file: {}", e))?;

    let class_name = format!("{}Animation", to_pascal_case(&project.name));
    let code = generate_animation_class(&project, &class_name, &args.package_name);

    // Write the Java file
    let java_dir = Path::new(&args.project_path)
        .join("src/main/java")
        .join(args.package_name.replace('.', "/"))
        .join("client/animation");

    fs::create_dir_all(&java_dir)
        .map_err(|e| format!("Failed to create directory: {}", e))?;

    let java_path = java_dir.join(format!("{}.java", class_name));
    fs::write(&java_path, &code)
        .map_err(|e| format!("Failed to write Java file: {}", e))?;

    Ok(GenerateResult {
        java_path: java_path.to_string_lossy().to_string(),
        java_code: code,
    })
}

fn generate_screen_class(
    project: &GuiProject,
    mod_id: &str,
    class_name: &str,
    handler_class: &str,
    package_name: &str,
) -> String {
    let slots: Vec<&GuiElement> = project.elements.iter().filter(|e| e.element_type == "slot").collect();
    let progress_bars: Vec<&GuiElement> = project.elements.iter().filter(|e| e.element_type == "progress_bar").collect();
    let energy_bars: Vec<&GuiElement> = project.elements.iter().filter(|e| e.element_type == "energy_bar").collect();
    let fluid_tanks: Vec<&GuiElement> = project.elements.iter().filter(|e| e.element_type == "fluid_tank").collect();
    let buttons: Vec<&GuiElement> = project.elements.iter().filter(|e| e.element_type == "button").collect();
    let labels: Vec<&GuiElement> = project.elements.iter().filter(|e| e.element_type == "label").collect();

    let mut code = format!(
        r#"package {package_name}.client.gui;

import net.alloymc.api.client.gui.Screen;
import net.alloymc.api.client.gui.ScreenHandler;
import net.alloymc.api.client.gui.widget.*;
import net.alloymc.api.util.ResourceLocation;
import net.alloymc.api.util.Text;

/**
 * Auto-generated GUI screen: {name}
 * Canvas size: {w}x{h}
 * Generated by Alloy IDE
 */
public class {class_name} extends Screen<{handler_class}> {{

    private static final ResourceLocation TEXTURE =
        new ResourceLocation("{mod_id}", "textures/gui/{name}.png");

    private static final int GUI_WIDTH = {w};
    private static final int GUI_HEIGHT = {h};

"#,
        package_name = package_name,
        name = project.name,
        w = project.width,
        h = project.height,
        class_name = class_name,
        handler_class = handler_class,
        mod_id = mod_id,
    );

    // Fields
    for (i, _) in progress_bars.iter().enumerate() {
        code.push_str(&format!("    private ProgressWidget progress{};\n", i));
    }
    for (i, _) in energy_bars.iter().enumerate() {
        code.push_str(&format!("    private EnergyBarWidget energy{};\n", i));
    }
    for (i, _) in fluid_tanks.iter().enumerate() {
        code.push_str(&format!("    private FluidTankWidget fluid{};\n", i));
    }
    if !progress_bars.is_empty() || !energy_bars.is_empty() || !fluid_tanks.is_empty() {
        code.push('\n');
    }

    // Constructor
    code.push_str(&format!(
        r#"    public {class_name}({handler_class} handler, Text title) {{
        super(handler, title);
        this.guiWidth = GUI_WIDTH;
        this.guiHeight = GUI_HEIGHT;
    }}

    @Override
    protected void init() {{
        super.init();
        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;

"#,
        class_name = class_name,
        handler_class = handler_class,
    ));

    // Slots
    if !slots.is_empty() {
        code.push_str("        // Inventory slots\n");
        for slot in &slots {
            let slot_id = slot.properties.get("slot_id").and_then(|v| v.as_i64()).unwrap_or(0);
            code.push_str(&format!(
                "        addSlot(new SlotWidget(handler.getInventory(), {}, x + {}, y + {}));\n",
                slot_id, slot.x, slot.y
            ));
        }
        code.push('\n');
    }

    // Progress bars
    if !progress_bars.is_empty() {
        code.push_str("        // Progress indicators\n");
        for (i, pb) in progress_bars.iter().enumerate() {
            let dir = pb.properties.get("direction").and_then(|v| v.as_str()).unwrap_or("right");
            code.push_str(&format!(
                "        progress{} = addWidget(new ProgressWidget(x + {}, y + {}, {}, {}, ProgressWidget.Direction.{}));\n",
                i, pb.x, pb.y, pb.width, pb.height, dir.to_uppercase()
            ));
        }
        code.push('\n');
    }

    // Energy bars
    if !energy_bars.is_empty() {
        code.push_str("        // Energy displays\n");
        for (i, eb) in energy_bars.iter().enumerate() {
            let max = eb.properties.get("max_value").and_then(|v| v.as_i64()).unwrap_or(10000);
            code.push_str(&format!(
                "        energy{} = addWidget(new EnergyBarWidget(x + {}, y + {}, {}, {}, {}));\n",
                i, eb.x, eb.y, eb.width, eb.height, max
            ));
        }
        code.push('\n');
    }

    // Fluid tanks
    if !fluid_tanks.is_empty() {
        code.push_str("        // Fluid tanks\n");
        for (i, ft) in fluid_tanks.iter().enumerate() {
            let max = ft.properties.get("max_mb").and_then(|v| v.as_i64()).unwrap_or(16000);
            code.push_str(&format!(
                "        fluid{} = addWidget(new FluidTankWidget(x + {}, y + {}, {}, {}, {}));\n",
                i, ft.x, ft.y, ft.width, ft.height, max
            ));
        }
        code.push('\n');
    }

    // Buttons
    if !buttons.is_empty() {
        code.push_str("        // Buttons\n");
        for (i, btn) in buttons.iter().enumerate() {
            let label = btn.label.as_deref().unwrap_or("");
            code.push_str(&format!(
                "        addWidget(new ButtonWidget(x + {}, y + {}, {}, {}, Text.literal(\"{}\"), button -> {{\n",
                btn.x, btn.y, btn.width, btn.height, label
            ));
            code.push_str(&format!("            // TODO: Handle button {} click\n", i));
            code.push_str("        }));\n");
        }
        code.push('\n');
    }

    code.push_str("    }\n\n");

    // drawBackground
    code.push_str(
        r#"    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, getGuiLeft(), getGuiTop(), 0, 0, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
"#,
    );

    // Labels
    if !labels.is_empty() {
        code.push_str("\n        // Text labels\n");
        for lbl in &labels {
            let color = lbl.properties.get("color").and_then(|v| v.as_str()).unwrap_or("#f0f0f4");
            let hex_color = format!("0x{}", color.trim_start_matches('#').to_uppercase());
            let align = lbl.properties.get("align").and_then(|v| v.as_str()).unwrap_or("left");
            let text = lbl.label.as_deref().unwrap_or("");

            if align == "center" {
                code.push_str(&format!(
                    "        context.drawCenteredText(this.textRenderer, \"{}\", getGuiLeft() + {} + {}, getGuiTop() + {}, {});\n",
                    text, lbl.x, lbl.width / 2, lbl.y, hex_color
                ));
            } else {
                code.push_str(&format!(
                    "        context.drawText(this.textRenderer, \"{}\", getGuiLeft() + {}, getGuiTop() + {}, {}, false);\n",
                    text, lbl.x, lbl.y, hex_color
                ));
            }
        }
    }

    code.push_str("\n        drawMouseoverTooltip(context, mouseX, mouseY);\n    }\n");

    // handlerTick
    if !progress_bars.is_empty() || !energy_bars.is_empty() || !fluid_tanks.is_empty() {
        code.push_str("\n    @Override\n    protected void handlerTick() {\n");
        for (i, _) in progress_bars.iter().enumerate() {
            let suffix = if i > 0 { format!("{}", i) } else { String::new() };
            code.push_str(&format!("        progress{}.setProgress(handler.getProgress{}());\n", i, suffix));
        }
        for (i, _) in energy_bars.iter().enumerate() {
            let suffix = if i > 0 { format!("{}", i) } else { String::new() };
            code.push_str(&format!("        energy{}.setEnergy(handler.getEnergy{}());\n", i, suffix));
        }
        for (i, _) in fluid_tanks.iter().enumerate() {
            let suffix = if i > 0 { format!("{}", i) } else { String::new() };
            code.push_str(&format!("        fluid{}.setFluid(handler.getFluid{}());\n", i, suffix));
        }
        code.push_str("    }\n");
    }

    code.push_str("}\n");
    code
}

fn generate_animation_class(
    project: &AnimProject,
    class_name: &str,
    package_name: &str,
) -> String {
    let mut code = format!(
        r#"package {package_name}.client.animation;

import net.alloymc.api.client.animation.Animation;
import net.alloymc.api.client.animation.AnimationTrack;
import net.alloymc.api.client.animation.Keyframe;
import net.alloymc.api.client.animation.Easing;

/**
 * Auto-generated animation: {name}
 * Duration: {duration} ticks ({seconds:.1}s @ 20 TPS)
 * Tracks: {track_count}
 * Generated by Alloy IDE
 */
public class {class_name} extends Animation {{

    public {class_name}() {{
        super({duration});
"#,
        package_name = package_name,
        name = project.name,
        duration = project.duration_ticks,
        seconds = project.duration_ticks as f64 / 20.0,
        track_count = project.tracks.len(),
        class_name = class_name,
    );

    if let Some(ref sheet) = project.sprite_sheet {
        code.push_str(&format!("\n        setSpriteSheet(\"{}\");\n", sheet));
        if let Some(fw) = project.frame_width {
            code.push_str(&format!("        setFrameWidth({});\n", fw));
        }
        if let Some(fh) = project.frame_height {
            code.push_str(&format!("        setFrameHeight({});\n", fh));
        }
    }

    code.push('\n');

    for (i, track) in project.tracks.iter().enumerate() {
        code.push_str(&format!(
            "        // Track: {}{}\n",
            track.property,
            if !track.target_element.is_empty() { format!(" (target: {})", track.target_element) } else { String::new() }
        ));
        code.push_str(&format!(
            "        AnimationTrack track{} = addTrack(AnimationTrack.Property.{});\n",
            i,
            track.property.to_uppercase()
        ));
        if !track.target_element.is_empty() {
            code.push_str(&format!("        track{}.setTarget(\"{}\");\n", i, track.target_element));
        }
        for kf in &track.keyframes {
            code.push_str(&format!(
                "        track{}.addKeyframe(new Keyframe({}, {}f, {}));\n",
                i, kf.tick, kf.value, easing_to_java(&kf.easing)
            ));
        }
        code.push('\n');
    }

    code.push_str("    }\n\n");

    // Convenience getters
    let mut seen = std::collections::HashSet::new();
    for track in &project.tracks {
        if seen.insert(&track.property) {
            let method_name = format!("get{}", to_pascal_case(&track.property));
            code.push_str(&format!(
                "    public float {}() {{\n        return getValue(AnimationTrack.Property.{});\n    }}\n\n",
                method_name,
                track.property.to_uppercase()
            ));
        }
    }

    code.push_str("}\n");
    code
}

fn easing_to_java(easing: &str) -> &str {
    match easing {
        "linear" => "Easing.LINEAR",
        "ease-in" => "Easing.EASE_IN",
        "ease-out" => "Easing.EASE_OUT",
        "ease-in-out" => "Easing.EASE_IN_OUT",
        "cubic-bezier" => "Easing.CUBIC_BEZIER",
        _ => "Easing.LINEAR",
    }
}

fn to_pascal_case(s: &str) -> String {
    s.split(|c: char| c == '-' || c == '_' || c == ' ')
        .filter(|w| !w.is_empty())
        .map(|word| {
            let mut chars = word.chars();
            match chars.next() {
                Some(c) => c.to_uppercase().to_string() + &chars.as_str().to_lowercase(),
                None => String::new(),
            }
        })
        .collect()
}

use crate::types::{ToolDefinition, ToolResult};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::path::Path;

#[derive(Debug, Serialize, Deserialize)]
struct GuiElement {
    #[serde(rename = "type")]
    element_type: String,
    id: String,
    x: i32,
    y: i32,
    width: i32,
    height: i32,
    #[serde(default)]
    properties: Value,
}

#[derive(Debug, Serialize, Deserialize)]
struct GuiProject {
    name: String,
    width: i32,
    height: i32,
    background_texture: Option<String>,
    elements: Vec<GuiElement>,
}

pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "gui_create".into(),
            description: "Create a .gui.json GUI definition file with canvas dimensions".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Path for the .gui.json file" },
                    "name": { "type": "string", "description": "GUI name (e.g. block name)" },
                    "width": { "type": "integer", "description": "Canvas width in pixels (default: 176)" },
                    "height": { "type": "integer", "description": "Canvas height in pixels (default: 166)" }
                },
                "required": ["path", "name"]
            }),
        },
        ToolDefinition {
            name: "gui_read".into(),
            description: "Read and parse a .gui.json file".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Path to the .gui.json file" }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "gui_update".into(),
            description: "Add, remove, or modify elements in a .gui.json file".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Path to the .gui.json file" },
                    "action": { "type": "string", "enum": ["add", "remove", "update", "set_background", "resize"], "description": "Action to perform" },
                    "element": { "type": "object", "description": "Element to add or update (for add/update)" },
                    "element_id": { "type": "string", "description": "Element ID to remove/update (for remove/update)" },
                    "background_texture": { "type": "string", "description": "Background texture path (for set_background)" },
                    "width": { "type": "integer", "description": "New canvas width (for resize)" },
                    "height": { "type": "integer", "description": "New canvas height (for resize)" }
                },
                "required": ["path", "action"]
            }),
        },
        ToolDefinition {
            name: "gui_generate_code".into(),
            description: "Generate Java screen class from a .gui.json GUI definition".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Path to the .gui.json file" },
                    "project_path": { "type": "string", "description": "Project root directory" },
                    "mod_id": { "type": "string", "description": "Mod ID for package/resource paths" }
                },
                "required": ["path", "project_path", "mod_id"]
            }),
        },
    ]
}

pub async fn execute(name: &str, params: Value) -> ToolResult {
    match name {
        "gui_create" => gui_create(params).await,
        "gui_read" => gui_read(params).await,
        "gui_update" => gui_update(params).await,
        "gui_generate_code" => gui_generate_code(params).await,
        _ => ToolResult::error(format!("Unknown gui tool: {}", name)),
    }
}

async fn gui_create(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };
    let name = match params.get("name").and_then(|v| v.as_str()) {
        Some(n) => n,
        None => return ToolResult::error("Missing required parameter: name"),
    };
    let width = params.get("width").and_then(|v| v.as_i64()).unwrap_or(176) as i32;
    let height = params.get("height").and_then(|v| v.as_i64()).unwrap_or(166) as i32;

    let gui = GuiProject {
        name: name.to_string(),
        width,
        height,
        background_texture: None,
        elements: Vec::new(),
    };

    let content = serde_json::to_string_pretty(&gui)
        .map_err(|e| format!("Serialization error: {}", e));

    match content {
        Ok(json_str) => {
            if let Some(parent) = Path::new(path).parent() {
                let _ = std::fs::create_dir_all(parent);
            }
            match std::fs::write(path, &json_str) {
                Ok(()) => ToolResult::json(&json!({
                    "path": path,
                    "name": name,
                    "width": width,
                    "height": height,
                    "status": "created"
                })),
                Err(e) => ToolResult::error(format!("Failed to write GUI file: {}", e)),
            }
        }
        Err(e) => ToolResult::error(e),
    }
}

async fn gui_read(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };

    let content = match std::fs::read_to_string(path) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(format!("Failed to read {}: {}", path, e)),
    };

    match serde_json::from_str::<Value>(&content) {
        Ok(parsed) => ToolResult::json(&parsed),
        Err(e) => ToolResult::error(format!("Failed to parse GUI JSON: {}", e)),
    }
}

async fn gui_update(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };
    let action = match params.get("action").and_then(|v| v.as_str()) {
        Some(a) => a,
        None => return ToolResult::error("Missing required parameter: action"),
    };

    let content = match std::fs::read_to_string(path) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(format!("Failed to read {}: {}", path, e)),
    };

    let mut gui: GuiProject = match serde_json::from_str(&content) {
        Ok(g) => g,
        Err(e) => return ToolResult::error(format!("Failed to parse GUI JSON: {}", e)),
    };

    match action {
        "add" => {
            let element = match params.get("element") {
                Some(e) => e,
                None => return ToolResult::error("Missing 'element' for add action"),
            };
            let elem: GuiElement = match serde_json::from_value(element.clone()) {
                Ok(e) => e,
                Err(e) => return ToolResult::error(format!("Invalid element: {}", e)),
            };
            gui.elements.push(elem);
        }
        "remove" => {
            let element_id = match params.get("element_id").and_then(|v| v.as_str()) {
                Some(id) => id,
                None => return ToolResult::error("Missing 'element_id' for remove action"),
            };
            let before = gui.elements.len();
            gui.elements.retain(|e| e.id != element_id);
            if gui.elements.len() == before {
                return ToolResult::error(format!("Element '{}' not found", element_id));
            }
        }
        "update" => {
            let element_id = match params.get("element_id").and_then(|v| v.as_str()) {
                Some(id) => id,
                None => return ToolResult::error("Missing 'element_id' for update action"),
            };
            let updates = match params.get("element") {
                Some(u) => u,
                None => return ToolResult::error("Missing 'element' with updates"),
            };
            let found = gui.elements.iter_mut().find(|e| e.id == element_id);
            match found {
                Some(elem) => {
                    if let Some(x) = updates.get("x").and_then(|v| v.as_i64()) {
                        elem.x = x as i32;
                    }
                    if let Some(y) = updates.get("y").and_then(|v| v.as_i64()) {
                        elem.y = y as i32;
                    }
                    if let Some(w) = updates.get("width").and_then(|v| v.as_i64()) {
                        elem.width = w as i32;
                    }
                    if let Some(h) = updates.get("height").and_then(|v| v.as_i64()) {
                        elem.height = h as i32;
                    }
                    if let Some(props) = updates.get("properties") {
                        elem.properties = props.clone();
                    }
                }
                None => return ToolResult::error(format!("Element '{}' not found", element_id)),
            }
        }
        "set_background" => {
            let texture = params.get("background_texture").and_then(|v| v.as_str()).map(String::from);
            gui.background_texture = texture;
        }
        "resize" => {
            if let Some(w) = params.get("width").and_then(|v| v.as_i64()) {
                gui.width = w as i32;
            }
            if let Some(h) = params.get("height").and_then(|v| v.as_i64()) {
                gui.height = h as i32;
            }
        }
        _ => return ToolResult::error(format!("Unknown action: {}", action)),
    }

    let updated = serde_json::to_string_pretty(&gui).unwrap();
    match std::fs::write(path, &updated) {
        Ok(()) => ToolResult::json(&json!({
            "path": path,
            "action": action,
            "element_count": gui.elements.len(),
            "status": "updated"
        })),
        Err(e) => ToolResult::error(format!("Failed to write {}: {}", path, e)),
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

async fn gui_generate_code(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };
    let project_path = match params.get("project_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: project_path"),
    };
    let mod_id = match params.get("mod_id").and_then(|v| v.as_str()) {
        Some(m) => m,
        None => return ToolResult::error("Missing required parameter: mod_id"),
    };

    let content = match std::fs::read_to_string(path) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(format!("Failed to read {}: {}", path, e)),
    };

    let gui: GuiProject = match serde_json::from_str(&content) {
        Ok(g) => g,
        Err(e) => return ToolResult::error(format!("Failed to parse GUI JSON: {}", e)),
    };

    let class_name = format!("{}Screen", to_pascal_case(&gui.name));
    let package_name = format!("com.{}", mod_id);
    let mut created_files = Vec::new();

    // Generate screen class
    let mut code = format!(
        r#"package {package_name}.gui;

import net.alloymc.api.gui.Screen;
import net.alloymc.api.gui.DrawContext;
import net.alloymc.api.util.Identifier;
"#,
        package_name = package_name,
    );

    // Check if we need slot imports
    let has_slots = gui.elements.iter().any(|e| e.element_type == "slot");
    if has_slots {
        code.push_str("import net.alloymc.api.gui.widget.SlotWidget;\n");
    }

    let has_progress = gui.elements.iter().any(|e| e.element_type == "progress_bar");
    if has_progress {
        code.push_str("import net.alloymc.api.gui.widget.ProgressBar;\n");
    }

    let has_buttons = gui.elements.iter().any(|e| e.element_type == "button");
    if has_buttons {
        code.push_str("import net.alloymc.api.gui.widget.Button;\n");
    }

    code.push_str(&format!(
        r#"
/**
 * GUI Screen for {name}
 * Generated by Alloy IDE GUI Editor
 */
public class {class_name} extends Screen {{

    private static final Identifier TEXTURE = new Identifier("{mod_id}", "textures/gui/{name}.png");
    private static final int GUI_WIDTH = {width};
    private static final int GUI_HEIGHT = {height};

    public {class_name}() {{
        super("{name}");
    }}

    @Override
    protected void init() {{
        super.init();
        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;
"#,
        name = gui.name,
        class_name = class_name,
        mod_id = mod_id,
        width = gui.width,
        height = gui.height,
    ));

    // Generate widget initialization
    for elem in &gui.elements {
        match elem.element_type.as_str() {
            "slot" => {
                code.push_str(&format!(
                    "        addSlot(new SlotWidget(x + {}, y + {}, {}));\n",
                    elem.x, elem.y, elem.width
                ));
            }
            "button" => {
                let label = elem.properties.get("label").and_then(|v| v.as_str()).unwrap_or("Button");
                code.push_str(&format!(
                    "        addButton(new Button(x + {}, y + {}, {}, {}, \"{}\", btn -> onButton{}Click()));\n",
                    elem.x, elem.y, elem.width, elem.height, label, to_pascal_case(&elem.id)
                ));
            }
            "progress_bar" => {
                code.push_str(&format!(
                    "        addProgressBar(new ProgressBar(x + {}, y + {}, {}, {}));\n",
                    elem.x, elem.y, elem.width, elem.height
                ));
            }
            _ => {
                code.push_str(&format!(
                    "        // TODO: Initialize {} element '{}' at ({}, {})\n",
                    elem.element_type, elem.id, elem.x, elem.y
                ));
            }
        }
    }

    code.push_str("    }\n\n");

    // Render method
    code.push_str(&format!(
        r#"    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {{
        renderBackground(context);
        int x = (this.width - GUI_WIDTH) / 2;
        int y = (this.height - GUI_HEIGHT) / 2;
        context.drawTexture(TEXTURE, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);
        super.render(context, mouseX, mouseY, delta);
    }}
"#
    ));

    // Button handlers
    for elem in gui.elements.iter().filter(|e| e.element_type == "button") {
        code.push_str(&format!(
            r#"
    private void onButton{}Click() {{
        // TODO: Handle button click
    }}
"#,
            to_pascal_case(&elem.id)
        ));
    }

    code.push_str("}\n");

    // Write the Java file
    let java_dir = Path::new(project_path)
        .join("src/main/java")
        .join(package_name.replace('.', "/"))
        .join("gui");

    if let Err(e) = std::fs::create_dir_all(&java_dir) {
        return ToolResult::error(format!("Failed to create directory: {}", e));
    }

    let java_path = java_dir.join(format!("{}.java", class_name));
    if let Err(e) = std::fs::write(&java_path, &code) {
        return ToolResult::error(format!("Failed to write Java file: {}", e));
    }

    created_files.push(json!({
        "path": java_path.to_string_lossy(),
        "type": "java_class"
    }));

    ToolResult::json(&json!({
        "created_files": created_files,
        "class_name": class_name,
        "package": format!("{}.gui", package_name),
    }))
}

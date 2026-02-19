use crate::types::{ToolDefinition, ToolResult};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::path::Path;

#[derive(Debug, Serialize, Deserialize)]
struct Keyframe {
    tick: u32,
    value: f64,
    easing: String,
}

#[derive(Debug, Serialize, Deserialize)]
struct AnimationTrack {
    property: String,
    keyframes: Vec<Keyframe>,
}

#[derive(Debug, Serialize, Deserialize)]
struct AnimationProject {
    name: String,
    duration_ticks: u32,
    looping: bool,
    tracks: Vec<AnimationTrack>,
}

pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "anim_create".into(),
            description: "Create a .anim.json animation definition file".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Path for the .anim.json file" },
                    "name": { "type": "string", "description": "Animation name" },
                    "duration_ticks": { "type": "integer", "description": "Duration in game ticks (20 ticks = 1 second, default: 20)" },
                    "looping": { "type": "boolean", "description": "Whether the animation loops (default: true)" }
                },
                "required": ["path", "name"]
            }),
        },
        ToolDefinition {
            name: "anim_read".into(),
            description: "Read and parse a .anim.json file".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Path to the .anim.json file" }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "anim_update".into(),
            description: "Add/remove tracks and keyframes in an animation".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Path to the .anim.json file" },
                    "action": { "type": "string", "enum": ["add_track", "remove_track", "add_keyframe", "remove_keyframe", "set_duration", "set_looping"], "description": "Action to perform" },
                    "property": { "type": "string", "description": "Track property name (e.g. 'uv_offset', 'opacity', 'color', 'scale', 'rotation')" },
                    "keyframe": { "type": "object", "description": "Keyframe with tick, value, easing" },
                    "tick": { "type": "integer", "description": "Tick to remove keyframe at (for remove_keyframe)" },
                    "duration_ticks": { "type": "integer", "description": "New duration (for set_duration)" },
                    "looping": { "type": "boolean", "description": "New looping value (for set_looping)" }
                },
                "required": ["path", "action"]
            }),
        },
        ToolDefinition {
            name: "anim_generate_code".into(),
            description: "Generate Java animation class from a .anim.json definition".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Path to the .anim.json file" },
                    "project_path": { "type": "string", "description": "Project root directory" },
                    "mod_id": { "type": "string", "description": "Mod ID for package paths" }
                },
                "required": ["path", "project_path", "mod_id"]
            }),
        },
    ]
}

pub async fn execute(name: &str, params: Value) -> ToolResult {
    match name {
        "anim_create" => anim_create(params).await,
        "anim_read" => anim_read(params).await,
        "anim_update" => anim_update(params).await,
        "anim_generate_code" => anim_generate_code(params).await,
        _ => ToolResult::error(format!("Unknown animation tool: {}", name)),
    }
}

async fn anim_create(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };
    let name = match params.get("name").and_then(|v| v.as_str()) {
        Some(n) => n,
        None => return ToolResult::error("Missing required parameter: name"),
    };
    let duration = params.get("duration_ticks").and_then(|v| v.as_u64()).unwrap_or(20) as u32;
    let looping = params.get("looping").and_then(|v| v.as_bool()).unwrap_or(true);

    let anim = AnimationProject {
        name: name.to_string(),
        duration_ticks: duration,
        looping,
        tracks: Vec::new(),
    };

    let content = serde_json::to_string_pretty(&anim).unwrap();
    if let Some(parent) = Path::new(path).parent() {
        let _ = std::fs::create_dir_all(parent);
    }

    match std::fs::write(path, &content) {
        Ok(()) => ToolResult::json(&json!({
            "path": path,
            "name": name,
            "duration_ticks": duration,
            "duration_seconds": duration as f64 / 20.0,
            "looping": looping,
            "status": "created"
        })),
        Err(e) => ToolResult::error(format!("Failed to write animation file: {}", e)),
    }
}

async fn anim_read(params: Value) -> ToolResult {
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
        Err(e) => ToolResult::error(format!("Failed to parse animation JSON: {}", e)),
    }
}

async fn anim_update(params: Value) -> ToolResult {
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

    let mut anim: AnimationProject = match serde_json::from_str(&content) {
        Ok(a) => a,
        Err(e) => return ToolResult::error(format!("Failed to parse animation JSON: {}", e)),
    };

    match action {
        "add_track" => {
            let property = match params.get("property").and_then(|v| v.as_str()) {
                Some(p) => p,
                None => return ToolResult::error("Missing 'property' for add_track"),
            };
            if anim.tracks.iter().any(|t| t.property == property) {
                return ToolResult::error(format!("Track '{}' already exists", property));
            }
            anim.tracks.push(AnimationTrack {
                property: property.to_string(),
                keyframes: Vec::new(),
            });
        }
        "remove_track" => {
            let property = match params.get("property").and_then(|v| v.as_str()) {
                Some(p) => p,
                None => return ToolResult::error("Missing 'property' for remove_track"),
            };
            let before = anim.tracks.len();
            anim.tracks.retain(|t| t.property != property);
            if anim.tracks.len() == before {
                return ToolResult::error(format!("Track '{}' not found", property));
            }
        }
        "add_keyframe" => {
            let property = match params.get("property").and_then(|v| v.as_str()) {
                Some(p) => p,
                None => return ToolResult::error("Missing 'property' for add_keyframe"),
            };
            let kf_value = match params.get("keyframe") {
                Some(k) => k,
                None => return ToolResult::error("Missing 'keyframe' for add_keyframe"),
            };
            let keyframe: Keyframe = match serde_json::from_value(kf_value.clone()) {
                Ok(k) => k,
                Err(e) => return ToolResult::error(format!("Invalid keyframe: {}", e)),
            };

            let track = match anim.tracks.iter_mut().find(|t| t.property == property) {
                Some(t) => t,
                None => return ToolResult::error(format!("Track '{}' not found. Add it first.", property)),
            };
            track.keyframes.push(keyframe);
            track.keyframes.sort_by_key(|k| k.tick);
        }
        "remove_keyframe" => {
            let property = match params.get("property").and_then(|v| v.as_str()) {
                Some(p) => p,
                None => return ToolResult::error("Missing 'property' for remove_keyframe"),
            };
            let tick = match params.get("tick").and_then(|v| v.as_u64()) {
                Some(t) => t as u32,
                None => return ToolResult::error("Missing 'tick' for remove_keyframe"),
            };

            let track = match anim.tracks.iter_mut().find(|t| t.property == property) {
                Some(t) => t,
                None => return ToolResult::error(format!("Track '{}' not found", property)),
            };
            track.keyframes.retain(|k| k.tick != tick);
        }
        "set_duration" => {
            let duration = match params.get("duration_ticks").and_then(|v| v.as_u64()) {
                Some(d) => d as u32,
                None => return ToolResult::error("Missing 'duration_ticks' for set_duration"),
            };
            anim.duration_ticks = duration;
        }
        "set_looping" => {
            let looping = match params.get("looping").and_then(|v| v.as_bool()) {
                Some(l) => l,
                None => return ToolResult::error("Missing 'looping' for set_looping"),
            };
            anim.looping = looping;
        }
        _ => return ToolResult::error(format!("Unknown action: {}", action)),
    }

    let updated = serde_json::to_string_pretty(&anim).unwrap();
    match std::fs::write(path, &updated) {
        Ok(()) => ToolResult::json(&json!({
            "path": path,
            "action": action,
            "track_count": anim.tracks.len(),
            "duration_ticks": anim.duration_ticks,
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

async fn anim_generate_code(params: Value) -> ToolResult {
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

    let anim: AnimationProject = match serde_json::from_str(&content) {
        Ok(a) => a,
        Err(e) => return ToolResult::error(format!("Failed to parse animation JSON: {}", e)),
    };

    let class_name = format!("{}Animation", to_pascal_case(&anim.name));
    let package_name = format!("com.{}", mod_id);

    let mut code = format!(
        r#"package {package_name}.animation;

import net.alloymc.api.client.animation.Animation;
import net.alloymc.api.client.animation.AnimationTrack;
import net.alloymc.api.client.animation.Keyframe;
import net.alloymc.api.client.animation.Easing;

/**
 * Animation: {name}
 * Duration: {duration} ticks ({seconds:.1}s) | Looping: {looping}
 * Generated by Alloy IDE Animation Editor
 */
public class {class_name} extends Animation {{

    public {class_name}() {{
        super({duration}, {looping});
"#,
        package_name = package_name,
        name = anim.name,
        class_name = class_name,
        duration = anim.duration_ticks,
        seconds = anim.duration_ticks as f64 / 20.0,
        looping = anim.looping,
    );

    for track in &anim.tracks {
        code.push_str(&format!(
            "\n        // Track: {}\n",
            track.property
        ));
        code.push_str(&format!(
            "        AnimationTrack {}_track = addTrack(\"{}\");\n",
            track.property.replace('.', "_"),
            track.property,
        ));

        for kf in &track.keyframes {
            code.push_str(&format!(
                "        {}_track.keyframe({}, {}f, Easing.{});\n",
                track.property.replace('.', "_"),
                kf.tick,
                kf.value,
                kf.easing.to_uppercase(),
            ));
        }
    }

    code.push_str("    }\n}\n");

    // Write the Java file
    let java_dir = Path::new(project_path)
        .join("src/main/java")
        .join(package_name.replace('.', "/"))
        .join("animation");

    if let Err(e) = std::fs::create_dir_all(&java_dir) {
        return ToolResult::error(format!("Failed to create directory: {}", e));
    }

    let java_path = java_dir.join(format!("{}.java", class_name));
    if let Err(e) = std::fs::write(&java_path, &code) {
        return ToolResult::error(format!("Failed to write Java file: {}", e));
    }

    ToolResult::json(&json!({
        "created_files": [{ "path": java_path.to_string_lossy(), "type": "java_class" }],
        "class_name": class_name,
        "package": format!("{}.animation", package_name),
        "duration_ticks": anim.duration_ticks,
        "track_count": anim.tracks.len(),
    }))
}

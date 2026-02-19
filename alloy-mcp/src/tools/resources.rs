use crate::state::{load_from_shared_file, ProjectState};
use serde_json::json;

/// Read an MCP resource by URI.
pub async fn read_resource(uri: &str, state: &ProjectState) -> String {
    match uri {
        "alloy://project" => resource_project(state),
        "alloy://file-tree" => resource_file_tree(state),
        "alloy://diagnostics" => resource_diagnostics(state),
        "alloy://api-reference" => resource_api_reference(),
        _ => format!("Unknown resource: {}", uri),
    }
}

fn resource_project(state: &ProjectState) -> String {
    // Clone out of the mutex to avoid holding the lock during fallback
    let in_memory = state
        .current_project
        .lock()
        .ok()
        .and_then(|guard| guard.clone());

    // Fall back to shared file if in-memory state is empty
    let info = match in_memory {
        Some(info) => info,
        None => match load_from_shared_file() {
            Some(loaded) => {
                // Update in-memory state for future calls
                if let Ok(mut guard) = state.current_project.lock() {
                    *guard = Some(loaded.clone());
                }
                loaded
            }
            None => {
                return "No project is currently open. Use project_open or project_create first."
                    .to_string();
            }
        },
    };

    let mut result = json!({
        "name": info.name,
        "path": info.path,
        "project_type": info.project_type,
        "environment": info.environment,
    });

    // Read manifest contents
    let mod_json = std::path::Path::new(&info.path).join("alloy.mod.json");
    let pack_toml = std::path::Path::new(&info.path).join("alloy.pack.toml");

    if mod_json.exists() {
        if let Ok(content) = std::fs::read_to_string(&mod_json) {
            if let Ok(parsed) = serde_json::from_str::<serde_json::Value>(&content) {
                result["manifest"] = parsed;
            }
        }
    } else if pack_toml.exists() {
        if let Ok(content) = std::fs::read_to_string(&pack_toml) {
            result["manifest_toml"] = serde_json::Value::String(content);
        }
    }

    serde_json::to_string_pretty(&result).unwrap_or_else(|_| "{}".to_string())
}

fn resource_file_tree(state: &ProjectState) -> String {
    let project_path = match state.project_path() {
        Ok(p) => p,
        Err(_) => return "No project open".to_string(),
    };

    let mut lines = Vec::new();
    build_tree(&project_path, "", &mut lines, 0, 4);
    lines.join("\n")
}

fn build_tree(path: &str, prefix: &str, lines: &mut Vec<String>, depth: usize, max_depth: usize) {
    if depth > max_depth {
        return;
    }

    let dir = match std::fs::read_dir(path) {
        Ok(d) => d,
        Err(_) => return,
    };

    let mut entries: Vec<_> = dir
        .flatten()
        .filter(|e| {
            let name = e.file_name().to_string_lossy().to_string();
            !name.starts_with('.')
                && name != "node_modules"
                && name != "target"
                && name != "build"
                && name != "dist"
                && name != ".gradle"
        })
        .collect();

    entries.sort_by(|a, b| {
        let a_dir = a.file_type().map(|t| t.is_dir()).unwrap_or(false);
        let b_dir = b.file_type().map(|t| t.is_dir()).unwrap_or(false);
        if a_dir == b_dir {
            a.file_name().cmp(&b.file_name())
        } else if a_dir {
            std::cmp::Ordering::Less
        } else {
            std::cmp::Ordering::Greater
        }
    });

    for (i, entry) in entries.iter().enumerate() {
        let name = entry.file_name().to_string_lossy().to_string();
        let is_last = i == entries.len() - 1;
        let connector = if is_last { "└── " } else { "├── " };
        let is_dir = entry.file_type().map(|t| t.is_dir()).unwrap_or(false);

        let display = if is_dir {
            format!("{}/", name)
        } else {
            name.clone()
        };

        lines.push(format!("{}{}{}", prefix, connector, display));

        if is_dir {
            let new_prefix = format!("{}{}", prefix, if is_last { "    " } else { "│   " });
            build_tree(
                &entry.path().to_string_lossy(),
                &new_prefix,
                lines,
                depth + 1,
                max_depth,
            );
        }
    }
}

fn resource_diagnostics(state: &ProjectState) -> String {
    let project_path = match state.project_path() {
        Ok(p) => p,
        Err(_) => return "No project open".to_string(),
    };

    let mut diagnostics = Vec::new();

    // Check build errors cache
    if let Ok(errors) = state.build_errors.lock() {
        for error in errors.iter() {
            diagnostics.push(json!({ "source": "build", "message": error }));
        }
    }

    // Quick environment check
    let mod_json_path = std::path::Path::new(&project_path).join("alloy.mod.json");
    if mod_json_path.exists() {
        if let Ok(content) = std::fs::read_to_string(&mod_json_path) {
            if let Ok(parsed) = serde_json::from_str::<serde_json::Value>(&content) {
                let env = parsed.get("environment").and_then(|e| e.as_str()).unwrap_or("both");
                if env != "both" {
                    diagnostics.push(json!({
                        "source": "environment",
                        "message": format!("Mod environment is '{}' — API surface is restricted", env)
                    }));
                }
            }
        }
    }

    serde_json::to_string_pretty(&json!({
        "diagnostics": diagnostics,
        "count": diagnostics.len()
    }))
    .unwrap_or_else(|_| "{}".to_string())
}

fn resource_api_reference() -> String {
    include_str!("../../resources/alloy_api_reference.md").to_string()
}

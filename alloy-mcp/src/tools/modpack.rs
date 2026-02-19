use crate::state::ProjectState;
use crate::types::{ToolDefinition, ToolResult};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, Clone)]
struct ModDependency {
    mod_id: String,
    version_constraint: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
struct ModpackMod {
    id: String,
    name: String,
    version: String,
    environment: String,
    source: String,
    source_path: Option<String>,
    enabled: bool,
    description: Option<String>,
    #[serde(default)]
    dependencies: Vec<ModDependency>,
}

#[derive(Debug, Serialize, Deserialize)]
struct ModpackManifest {
    name: String,
    version: String,
    minecraft_version: String,
    alloy_version: String,
    mods: Vec<ModpackMod>,
}

impl Default for ModpackManifest {
    fn default() -> Self {
        Self {
            name: "My Modpack".to_string(),
            version: "1.0.0".to_string(),
            minecraft_version: "1.21.4".to_string(),
            alloy_version: "0.1.0".to_string(),
            mods: Vec::new(),
        }
    }
}

pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "modpack_load".into(),
            description: "Load and parse the alloy.pack.toml modpack manifest".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "project_path": { "type": "string", "description": "Path to the modpack project" }
                },
                "required": ["project_path"]
            }),
        },
        ToolDefinition {
            name: "modpack_save".into(),
            description: "Save modpack manifest back to alloy.pack.toml".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "project_path": { "type": "string", "description": "Path to the modpack project" },
                    "manifest": { "type": "object", "description": "Full manifest object to save" }
                },
                "required": ["project_path", "manifest"]
            }),
        },
        ToolDefinition {
            name: "modpack_add_mod".into(),
            description: "Add a mod to the modpack from a JAR file path".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "project_path": { "type": "string", "description": "Path to the modpack project" },
                    "jar_path": { "type": "string", "description": "Path to the mod JAR file" }
                },
                "required": ["project_path", "jar_path"]
            }),
        },
        ToolDefinition {
            name: "modpack_remove_mod".into(),
            description: "Remove a mod from the modpack by ID".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "project_path": { "type": "string", "description": "Path to the modpack project" },
                    "mod_id": { "type": "string", "description": "ID of the mod to remove" }
                },
                "required": ["project_path", "mod_id"]
            }),
        },
        ToolDefinition {
            name: "modpack_check_conflicts".into(),
            description: "Detect dependency, version, and environment conflicts in the modpack".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "project_path": { "type": "string", "description": "Path to the modpack project" }
                },
                "required": ["project_path"]
            }),
        },
        ToolDefinition {
            name: "modpack_export".into(),
            description: "Export modpack to .alloypack format (ZIP)".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "project_path": { "type": "string", "description": "Path to the modpack project" },
                    "output_path": { "type": "string", "description": "Output .alloypack file path" }
                },
                "required": ["project_path", "output_path"]
            }),
        },
        ToolDefinition {
            name: "modpack_config".into(),
            description: "Read or write a mod's config file in the modpack".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "project_path": { "type": "string", "description": "Path to the modpack project" },
                    "mod_id": { "type": "string", "description": "Mod ID" },
                    "action": { "type": "string", "enum": ["read", "write"], "description": "Read or write config" },
                    "content": { "type": "string", "description": "Config content to write (for write action)" },
                    "format": { "type": "string", "enum": ["toml", "json"], "description": "Config format (default: toml)" }
                },
                "required": ["project_path", "mod_id", "action"]
            }),
        },
    ]
}

pub async fn execute(name: &str, params: Value, _state: &ProjectState) -> ToolResult {
    match name {
        "modpack_load" => modpack_load(params).await,
        "modpack_save" => modpack_save(params).await,
        "modpack_add_mod" => modpack_add_mod(params).await,
        "modpack_remove_mod" => modpack_remove_mod(params).await,
        "modpack_check_conflicts" => modpack_check_conflicts(params).await,
        "modpack_export" => modpack_export(params).await,
        "modpack_config" => modpack_config(params).await,
        _ => ToolResult::error(format!("Unknown modpack tool: {}", name)),
    }
}

fn load_manifest(project_path: &str) -> Result<ModpackManifest, String> {
    let manifest_path = Path::new(project_path).join("alloy.pack.toml");

    if !manifest_path.exists() {
        return Ok(ModpackManifest::default());
    }

    let content = std::fs::read_to_string(&manifest_path)
        .map_err(|e| format!("Failed to read manifest: {}", e))?;

    let parsed: toml::Value = content
        .parse()
        .map_err(|e| format!("Failed to parse TOML: {}", e))?;

    let name = parsed.get("pack").and_then(|p| p.get("name")).and_then(|n| n.as_str()).unwrap_or("My Modpack").to_string();
    let version = parsed.get("pack").and_then(|p| p.get("version")).and_then(|v| v.as_str()).unwrap_or("1.0.0").to_string();
    let minecraft_version = parsed.get("pack").and_then(|p| p.get("minecraft_version")).and_then(|v| v.as_str()).unwrap_or("1.21.4").to_string();
    let alloy_version = parsed.get("pack").and_then(|p| p.get("alloy_version")).and_then(|v| v.as_str()).unwrap_or("0.1.0").to_string();

    let mods = parsed.get("mods").and_then(|m| m.as_array()).map(|arr| {
        arr.iter().filter_map(|m| {
            let dependencies = m.get("dependencies").and_then(|d| d.as_array()).map(|deps| {
                deps.iter().filter_map(|dep| {
                    Some(ModDependency {
                        mod_id: dep.get("mod_id")?.as_str()?.to_string(),
                        version_constraint: dep.get("version_constraint").and_then(|v| v.as_str()).unwrap_or("*").to_string(),
                    })
                }).collect()
            }).unwrap_or_default();

            Some(ModpackMod {
                id: m.get("id")?.as_str()?.to_string(),
                name: m.get("name")?.as_str()?.to_string(),
                version: m.get("version").and_then(|v| v.as_str()).unwrap_or("*").to_string(),
                environment: m.get("environment").and_then(|e| e.as_str()).unwrap_or("both").to_string(),
                source: m.get("source").and_then(|s| s.as_str()).unwrap_or("local").to_string(),
                source_path: m.get("source_path").and_then(|s| s.as_str()).map(String::from),
                enabled: m.get("enabled").and_then(|e| e.as_bool()).unwrap_or(true),
                description: m.get("description").and_then(|d| d.as_str()).map(String::from),
                dependencies,
            })
        }).collect()
    }).unwrap_or_default();

    Ok(ModpackManifest { name, version, minecraft_version, alloy_version, mods })
}

fn save_manifest(project_path: &str, manifest: &ModpackManifest) -> Result<(), String> {
    let manifest_path = Path::new(project_path).join("alloy.pack.toml");

    let mut content = String::new();
    content.push_str("[pack]\n");
    content.push_str(&format!("name = \"{}\"\n", manifest.name));
    content.push_str(&format!("version = \"{}\"\n", manifest.version));
    content.push_str(&format!("minecraft_version = \"{}\"\n", manifest.minecraft_version));
    content.push_str(&format!("alloy_version = \"{}\"\n", manifest.alloy_version));
    content.push('\n');

    if !manifest.mods.is_empty() {
        for m in &manifest.mods {
            content.push_str("\n[[mods]]\n");
            content.push_str(&format!("id = \"{}\"\n", m.id));
            content.push_str(&format!("name = \"{}\"\n", m.name));
            content.push_str(&format!("version = \"{}\"\n", m.version));
            content.push_str(&format!("environment = \"{}\"\n", m.environment));
            content.push_str(&format!("source = \"{}\"\n", m.source));
            if let Some(ref sp) = m.source_path {
                content.push_str(&format!("source_path = \"{}\"\n", sp));
            }
            if !m.enabled {
                content.push_str("enabled = false\n");
            }
            if let Some(ref desc) = m.description {
                content.push_str(&format!("description = \"{}\"\n", desc));
            }
            for dep in &m.dependencies {
                content.push_str(&format!(
                    "[[mods.dependencies]]\nmod_id = \"{}\"\nversion_constraint = \"{}\"\n",
                    dep.mod_id, dep.version_constraint
                ));
            }
        }
    }

    std::fs::write(&manifest_path, content).map_err(|e| format!("Failed to write manifest: {}", e))
}

async fn modpack_load(params: Value) -> ToolResult {
    let project_path = match params.get("project_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: project_path"),
    };

    match load_manifest(project_path) {
        Ok(manifest) => ToolResult::json(&manifest),
        Err(e) => ToolResult::error(e),
    }
}

async fn modpack_save(params: Value) -> ToolResult {
    let project_path = match params.get("project_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: project_path"),
    };
    let manifest_value = match params.get("manifest") {
        Some(m) => m,
        None => return ToolResult::error("Missing required parameter: manifest"),
    };

    let manifest: ModpackManifest = match serde_json::from_value(manifest_value.clone()) {
        Ok(m) => m,
        Err(e) => return ToolResult::error(format!("Invalid manifest: {}", e)),
    };

    match save_manifest(project_path, &manifest) {
        Ok(()) => ToolResult::text(format!("Saved manifest for \"{}\"", manifest.name)),
        Err(e) => ToolResult::error(e),
    }
}

async fn modpack_add_mod(params: Value) -> ToolResult {
    let project_path = match params.get("project_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: project_path"),
    };
    let jar_path = match params.get("jar_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: jar_path"),
    };

    let jar = Path::new(jar_path);
    if !jar.exists() {
        return ToolResult::error("JAR file not found");
    }

    let file_name = jar.file_stem().and_then(|s| s.to_str()).unwrap_or("unknown");

    let mods_dir = Path::new(project_path).join("mods");
    if let Err(e) = std::fs::create_dir_all(&mods_dir) {
        return ToolResult::error(format!("Failed to create mods directory: {}", e));
    }

    let dest = mods_dir.join(jar.file_name().unwrap_or_default());
    if let Err(e) = std::fs::copy(jar_path, &dest) {
        return ToolResult::error(format!("Failed to copy JAR: {}", e));
    }

    let id: String = file_name.to_lowercase().replace(' ', "-")
        .chars().filter(|c| c.is_alphanumeric() || *c == '-').collect();

    let new_mod = ModpackMod {
        id: id.clone(),
        name: file_name.to_string(),
        version: "*".to_string(),
        environment: "both".to_string(),
        source: "jar".to_string(),
        source_path: Some(dest.to_string_lossy().to_string()),
        enabled: true,
        description: None,
        dependencies: Vec::new(),
    };

    // Update manifest
    let mut manifest = load_manifest(project_path).unwrap_or_default();
    manifest.mods.push(new_mod.clone());
    if let Err(e) = save_manifest(project_path, &manifest) {
        return ToolResult::error(e);
    }

    ToolResult::json(&new_mod)
}

async fn modpack_remove_mod(params: Value) -> ToolResult {
    let project_path = match params.get("project_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: project_path"),
    };
    let mod_id = match params.get("mod_id").and_then(|v| v.as_str()) {
        Some(id) => id,
        None => return ToolResult::error("Missing required parameter: mod_id"),
    };

    let mut manifest = match load_manifest(project_path) {
        Ok(m) => m,
        Err(e) => return ToolResult::error(e),
    };

    // Remove JAR file if exists
    if let Some(m) = manifest.mods.iter().find(|m| m.id == mod_id) {
        if let Some(ref source_path) = m.source_path {
            let path = Path::new(source_path);
            if path.exists() && path.starts_with(Path::new(project_path).join("mods")) {
                let _ = std::fs::remove_file(path);
            }
        }
    }

    let before = manifest.mods.len();
    manifest.mods.retain(|m| m.id != mod_id);

    if manifest.mods.len() == before {
        return ToolResult::error(format!("Mod '{}' not found in manifest", mod_id));
    }

    match save_manifest(project_path, &manifest) {
        Ok(()) => ToolResult::text(format!("Removed mod '{}'", mod_id)),
        Err(e) => ToolResult::error(e),
    }
}

fn satisfies_version(version: &str, constraint: &str) -> bool {
    let constraint = constraint.trim();
    if constraint == "*" || constraint.is_empty() { return true; }

    let parse_ver = |s: &str| -> Option<(u64, u64, u64)> {
        let parts: Vec<&str> = s.trim().split('.').collect();
        match parts.len() {
            3.. => Some((parts[0].parse().ok()?, parts[1].parse().ok()?, parts[2].parse().ok()?)),
            2 => Some((parts[0].parse().ok()?, parts[1].parse().ok()?, 0)),
            1 => Some((parts[0].parse().ok()?, 0, 0)),
            _ => None,
        }
    };

    let ver = match parse_ver(version) { Some(v) => v, None => return false };

    if constraint.starts_with(">=") {
        return parse_ver(&constraint[2..]).map_or(false, |min| ver >= min);
    }
    if constraint.starts_with('^') {
        return parse_ver(&constraint[1..]).map_or(false, |base| ver >= base && ver.0 == base.0);
    }
    if constraint.starts_with('~') {
        return parse_ver(&constraint[1..]).map_or(false, |base| ver >= base && ver.0 == base.0 && ver.1 == base.1);
    }

    parse_ver(constraint).map_or(true, |exact| ver == exact)
}

async fn modpack_check_conflicts(params: Value) -> ToolResult {
    let project_path = match params.get("project_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: project_path"),
    };

    let manifest = match load_manifest(project_path) {
        Ok(m) => m,
        Err(e) => return ToolResult::error(e),
    };

    let mut conflicts: Vec<Value> = Vec::new();
    let enabled_mods: Vec<&ModpackMod> = manifest.mods.iter().filter(|m| m.enabled).collect();
    let mod_map: HashMap<&str, &ModpackMod> = enabled_mods.iter().map(|m| (m.id.as_str(), *m)).collect();

    // Duplicate IDs
    let mut id_counts: HashMap<&str, Vec<&str>> = HashMap::new();
    for m in &enabled_mods {
        id_counts.entry(m.id.as_str()).or_default().push(m.name.as_str());
    }
    for (id, names) in &id_counts {
        if names.len() > 1 {
            conflicts.push(json!({
                "kind": "duplicate_id",
                "mod_id": id,
                "details": format!("Multiple mods share ID \"{}\": {}", id, names.join(", ")),
                "suggestion": "Rename one of the mods to have a unique ID"
            }));
        }
    }

    // Missing dependencies and version mismatches
    for m in &enabled_mods {
        for dep in &m.dependencies {
            match mod_map.get(dep.mod_id.as_str()) {
                None => {
                    conflicts.push(json!({
                        "kind": "missing_dependency",
                        "mod_id": m.id,
                        "details": format!("\"{}\" requires \"{}\" ({}) which is not in the modpack", m.name, dep.mod_id, dep.version_constraint),
                        "suggestion": format!("Add \"{}\" to the modpack", dep.mod_id)
                    }));
                }
                Some(dep_mod) => {
                    if dep.version_constraint != "*" && dep_mod.version != "*" && !satisfies_version(&dep_mod.version, &dep.version_constraint) {
                        conflicts.push(json!({
                            "kind": "version_mismatch",
                            "mod_id": m.id,
                            "details": format!("\"{}\" requires \"{}\" {} but version {} is installed", m.name, dep.mod_id, dep.version_constraint, dep_mod.version),
                            "suggestion": format!("Update \"{}\" to match {}", dep_mod.name, dep.version_constraint)
                        }));
                    }
                }
            }
        }
    }

    // Environment conflicts
    for m in &enabled_mods {
        for dep in &m.dependencies {
            if let Some(dep_mod) = mod_map.get(dep.mod_id.as_str()) {
                if m.environment == "server" && dep_mod.environment == "client" {
                    conflicts.push(json!({
                        "kind": "environment_conflict",
                        "mod_id": m.id,
                        "details": format!("Server mod \"{}\" depends on client-only mod \"{}\"", m.name, dep_mod.name),
                        "suggestion": format!("Change \"{}\" environment to \"both\"", dep_mod.name)
                    }));
                } else if m.environment == "client" && dep_mod.environment == "server" {
                    conflicts.push(json!({
                        "kind": "environment_conflict",
                        "mod_id": m.id,
                        "details": format!("Client mod \"{}\" depends on server-only mod \"{}\"", m.name, dep_mod.name),
                        "suggestion": format!("Change \"{}\" environment to \"both\"", dep_mod.name)
                    }));
                }
            }
        }
    }

    // Mixed environment warning
    let server_only: Vec<&str> = enabled_mods.iter().filter(|m| m.environment == "server").map(|m| m.name.as_str()).collect();
    let client_only: Vec<&str> = enabled_mods.iter().filter(|m| m.environment == "client").map(|m| m.name.as_str()).collect();
    if !server_only.is_empty() && !client_only.is_empty() {
        conflicts.push(json!({
            "kind": "environment_conflict",
            "mod_id": "",
            "details": format!("Pack mixes client-only mods ({}) with server-only mods ({})", client_only.join(", "), server_only.join(", ")),
            "suggestion": "Verify this is intentional"
        }));
    }

    ToolResult::json(&json!({ "conflicts": conflicts, "count": conflicts.len() }))
}

async fn modpack_export(params: Value) -> ToolResult {
    use std::io::Write;

    let project_path = match params.get("project_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: project_path"),
    };
    let output_path = match params.get("output_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: output_path"),
    };

    let project = Path::new(project_path);
    let manifest = match load_manifest(project_path) {
        Ok(m) => m,
        Err(e) => return ToolResult::error(e),
    };

    let file = match std::fs::File::create(output_path) {
        Ok(f) => f,
        Err(e) => return ToolResult::error(format!("Failed to create output file: {}", e)),
    };

    let mut zip = zip::ZipWriter::new(file);
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated);

    // Write manifest
    let manifest_path = project.join("alloy.pack.toml");
    if manifest_path.exists() {
        if let Ok(content) = std::fs::read_to_string(&manifest_path) {
            let _ = zip.start_file("alloy.pack.toml", options);
            let _ = zip.write_all(content.as_bytes());
        }
    }

    // Write mod JARs
    let mut included = 0;
    for m in &manifest.mods {
        if !m.enabled { continue; }
        if let Some(ref sp) = m.source_path {
            let jar_path = Path::new(sp);
            if jar_path.exists() {
                let jar_name = jar_path.file_name().unwrap_or_default().to_string_lossy();
                if let Ok(data) = std::fs::read(jar_path) {
                    let _ = zip.start_file(format!("mods/{}", jar_name), options);
                    let _ = zip.write_all(&data);
                    included += 1;
                }
            }
        }
    }

    // Write metadata
    let metadata = json!({
        "format": "alloypack",
        "format_version": 1,
        "name": manifest.name,
        "version": manifest.version,
        "minecraft_version": manifest.minecraft_version,
        "alloy_version": manifest.alloy_version,
        "mod_count": manifest.mods.iter().filter(|m| m.enabled).count(),
    });
    let _ = zip.start_file("pack.json", options);
    let _ = zip.write_all(serde_json::to_string_pretty(&metadata).unwrap().as_bytes());

    // Include configs
    let config_dir = project.join("config");
    if config_dir.exists() {
        if let Ok(entries) = std::fs::read_dir(&config_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_file() {
                    let name = path.file_name().unwrap_or_default().to_string_lossy();
                    if let Ok(data) = std::fs::read(&path) {
                        let _ = zip.start_file(format!("config/{}", name), options);
                        let _ = zip.write_all(&data);
                    }
                }
            }
        }
    }

    let _ = zip.finish();

    ToolResult::json(&json!({
        "output": output_path,
        "name": manifest.name,
        "mods_included": included,
        "status": "exported"
    }))
}

async fn modpack_config(params: Value) -> ToolResult {
    let project_path = match params.get("project_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: project_path"),
    };
    let mod_id = match params.get("mod_id").and_then(|v| v.as_str()) {
        Some(id) => id,
        None => return ToolResult::error("Missing required parameter: mod_id"),
    };
    let action = match params.get("action").and_then(|v| v.as_str()) {
        Some(a) => a,
        None => return ToolResult::error("Missing required parameter: action"),
    };

    let config_dir = Path::new(project_path).join("config");

    match action {
        "read" => {
            let toml_path = config_dir.join(format!("{}.toml", mod_id));
            if toml_path.exists() {
                return match std::fs::read_to_string(&toml_path) {
                    Ok(c) => ToolResult::text(c),
                    Err(e) => ToolResult::error(format!("Failed to read config: {}", e)),
                };
            }
            let json_path = config_dir.join(format!("{}.json", mod_id));
            if json_path.exists() {
                return match std::fs::read_to_string(&json_path) {
                    Ok(c) => ToolResult::text(c),
                    Err(e) => ToolResult::error(format!("Failed to read config: {}", e)),
                };
            }
            ToolResult::text(format!("# Configuration for {}\n# Edit values below and save\n", mod_id))
        }
        "write" => {
            let content = match params.get("content").and_then(|v| v.as_str()) {
                Some(c) => c,
                None => return ToolResult::error("Missing 'content' for write action"),
            };
            let format = params.get("format").and_then(|v| v.as_str()).unwrap_or("toml");

            if let Err(e) = std::fs::create_dir_all(&config_dir) {
                return ToolResult::error(format!("Failed to create config directory: {}", e));
            }

            let ext = if format == "json" { "json" } else { "toml" };
            let config_path = config_dir.join(format!("{}.{}", mod_id, ext));

            match std::fs::write(&config_path, content) {
                Ok(()) => ToolResult::text(format!("Saved config for '{}'", mod_id)),
                Err(e) => ToolResult::error(format!("Failed to write config: {}", e)),
            }
        }
        _ => ToolResult::error(format!("Unknown action: {}. Use 'read' or 'write'", action)),
    }
}

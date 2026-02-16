use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ModpackMod {
    pub id: String,
    pub name: String,
    pub version: String,
    pub environment: String,
    pub source: String,
    pub source_path: Option<String>,
    pub enabled: bool,
    pub description: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ModpackManifest {
    pub name: String,
    pub version: String,
    pub minecraft_version: String,
    pub alloy_version: String,
    pub mods: Vec<ModpackMod>,
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

/// Load modpack manifest from alloy.pack.toml in the project directory
#[tauri::command]
pub async fn load_modpack_manifest(project_path: String) -> Result<ModpackManifest, String> {
    let manifest_path = Path::new(&project_path).join("alloy.pack.toml");

    if !manifest_path.exists() {
        return Ok(ModpackManifest::default());
    }

    let content = std::fs::read_to_string(&manifest_path)
        .map_err(|e| format!("Failed to read manifest: {}", e))?;

    // Parse the TOML manifest
    let parsed: toml::Value = content
        .parse()
        .map_err(|e| format!("Failed to parse TOML: {}", e))?;

    let name = parsed
        .get("pack")
        .and_then(|p| p.get("name"))
        .and_then(|n| n.as_str())
        .unwrap_or("My Modpack")
        .to_string();

    let version = parsed
        .get("pack")
        .and_then(|p| p.get("version"))
        .and_then(|v| v.as_str())
        .unwrap_or("1.0.0")
        .to_string();

    let minecraft_version = parsed
        .get("pack")
        .and_then(|p| p.get("minecraft_version"))
        .and_then(|v| v.as_str())
        .unwrap_or("1.21.4")
        .to_string();

    let alloy_version = parsed
        .get("pack")
        .and_then(|p| p.get("alloy_version"))
        .and_then(|v| v.as_str())
        .unwrap_or("0.1.0")
        .to_string();

    let mods = parsed
        .get("mods")
        .and_then(|m| m.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|m| {
                    Some(ModpackMod {
                        id: m.get("id")?.as_str()?.to_string(),
                        name: m.get("name")?.as_str()?.to_string(),
                        version: m.get("version").and_then(|v| v.as_str()).unwrap_or("*").to_string(),
                        environment: m
                            .get("environment")
                            .and_then(|e| e.as_str())
                            .unwrap_or("both")
                            .to_string(),
                        source: m
                            .get("source")
                            .and_then(|s| s.as_str())
                            .unwrap_or("local")
                            .to_string(),
                        source_path: m.get("source_path").and_then(|s| s.as_str()).map(String::from),
                        enabled: m.get("enabled").and_then(|e| e.as_bool()).unwrap_or(true),
                        description: m.get("description").and_then(|d| d.as_str()).map(String::from),
                    })
                })
                .collect()
        })
        .unwrap_or_default();

    Ok(ModpackManifest {
        name,
        version,
        minecraft_version,
        alloy_version,
        mods,
    })
}

/// Save modpack manifest to alloy.pack.toml
#[tauri::command]
pub async fn save_modpack_manifest(
    project_path: String,
    manifest: ModpackManifest,
) -> Result<(), String> {
    let manifest_path = Path::new(&project_path).join("alloy.pack.toml");

    let mut content = String::new();
    content.push_str("[pack]\n");
    content.push_str(&format!("name = \"{}\"\n", manifest.name));
    content.push_str(&format!("version = \"{}\"\n", manifest.version));
    content.push_str(&format!(
        "minecraft_version = \"{}\"\n",
        manifest.minecraft_version
    ));
    content.push_str(&format!("alloy_version = \"{}\"\n", manifest.alloy_version));
    content.push('\n');

    if !manifest.mods.is_empty() {
        content.push_str("# Mods included in this modpack\n");
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
        }
    }

    std::fs::write(&manifest_path, content)
        .map_err(|e| format!("Failed to write manifest: {}", e))?;

    Ok(())
}

/// Add a mod to the modpack from a JAR file
#[tauri::command]
pub async fn add_mod_from_jar(
    project_path: String,
    jar_path: String,
) -> Result<ModpackMod, String> {
    let jar = Path::new(&jar_path);
    if !jar.exists() {
        return Err("JAR file not found".to_string());
    }

    let file_name = jar
        .file_stem()
        .and_then(|s| s.to_str())
        .unwrap_or("unknown");

    // Copy JAR to the modpack's mods/ directory
    let mods_dir = Path::new(&project_path).join("mods");
    std::fs::create_dir_all(&mods_dir)
        .map_err(|e| format!("Failed to create mods directory: {}", e))?;

    let dest = mods_dir.join(jar.file_name().unwrap_or_default());
    std::fs::copy(&jar_path, &dest)
        .map_err(|e| format!("Failed to copy JAR: {}", e))?;

    // Generate a mod entry from the file name
    let id = file_name
        .to_lowercase()
        .replace(' ', "-")
        .chars()
        .filter(|c| c.is_alphanumeric() || *c == '-')
        .collect::<String>();

    Ok(ModpackMod {
        id,
        name: file_name.to_string(),
        version: "*".to_string(),
        environment: "both".to_string(),
        source: "jar".to_string(),
        source_path: Some(dest.to_string_lossy().to_string()),
        enabled: true,
        description: None,
    })
}

/// Remove a mod from the modpack
#[tauri::command]
pub async fn remove_mod_from_pack(
    project_path: String,
    mod_id: String,
) -> Result<(), String> {
    // Load current manifest
    let manifest = load_modpack_manifest(project_path.clone()).await?;

    // Find and remove the mod's JAR if it exists in mods/
    if let Some(m) = manifest.mods.iter().find(|m| m.id == mod_id) {
        if let Some(ref source_path) = m.source_path {
            let path = Path::new(source_path);
            if path.exists() && path.starts_with(Path::new(&project_path).join("mods")) {
                let _ = std::fs::remove_file(path);
            }
        }
    }

    // Save updated manifest without the removed mod
    let updated = ModpackManifest {
        mods: manifest.mods.into_iter().filter(|m| m.id != mod_id).collect(),
        ..manifest
    };

    save_modpack_manifest(project_path, updated).await?;

    Ok(())
}

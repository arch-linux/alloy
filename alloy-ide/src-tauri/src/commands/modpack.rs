use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ModDependency {
    pub mod_id: String,
    pub version_constraint: String,
}

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
    #[serde(default)]
    pub dependencies: Vec<ModDependency>,
}

#[derive(Debug, Serialize, Clone)]
pub struct ModConflict {
    pub kind: String,
    pub mod_id: String,
    pub mod_name: String,
    pub details: String,
    pub affected_mods: Vec<String>,
    pub suggestion: Option<String>,
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
                    {
                        let dependencies = m
                            .get("dependencies")
                            .and_then(|d| d.as_array())
                            .map(|deps| {
                                deps.iter()
                                    .filter_map(|dep| {
                                        Some(ModDependency {
                                            mod_id: dep.get("mod_id")?.as_str()?.to_string(),
                                            version_constraint: dep
                                                .get("version_constraint")
                                                .and_then(|v| v.as_str())
                                                .unwrap_or("*")
                                                .to_string(),
                                        })
                                    })
                                    .collect()
                            })
                            .unwrap_or_default();

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
                            dependencies,
                        })
                    }
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
            if !m.dependencies.is_empty() {
                for dep in &m.dependencies {
                    content.push_str(&format!(
                        "[[mods.dependencies]]\nmod_id = \"{}\"\nversion_constraint = \"{}\"\n",
                        dep.mod_id, dep.version_constraint
                    ));
                }
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
        dependencies: Vec::new(),
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

/// Simple semver satisfaction check.
/// Supports: "*", "1.2.3" (exact), ">=1.2.0", "^1.2.0" (>=1.2.0 <2.0.0), "~1.2.0" (>=1.2.0 <1.3.0)
fn satisfies_version(version: &str, constraint: &str) -> bool {
    let constraint = constraint.trim();
    if constraint == "*" || constraint.is_empty() {
        return true;
    }

    let parse_ver = |s: &str| -> Option<(u64, u64, u64)> {
        let parts: Vec<&str> = s.trim().split('.').collect();
        if parts.len() >= 3 {
            Some((
                parts[0].parse().ok()?,
                parts[1].parse().ok()?,
                parts[2].parse().ok()?,
            ))
        } else if parts.len() == 2 {
            Some((parts[0].parse().ok()?, parts[1].parse().ok()?, 0))
        } else if parts.len() == 1 {
            Some((parts[0].parse().ok()?, 0, 0))
        } else {
            None
        }
    };

    let ver = match parse_ver(version) {
        Some(v) => v,
        None => return false,
    };

    if constraint.starts_with(">=") {
        let min = match parse_ver(&constraint[2..]) {
            Some(v) => v,
            None => return false,
        };
        return ver >= min;
    }

    if constraint.starts_with('^') {
        let base = match parse_ver(&constraint[1..]) {
            Some(v) => v,
            None => return false,
        };
        // ^1.2.3 means >=1.2.3 <2.0.0
        return ver >= base && ver.0 == base.0;
    }

    if constraint.starts_with('~') {
        let base = match parse_ver(&constraint[1..]) {
            Some(v) => v,
            None => return false,
        };
        // ~1.2.3 means >=1.2.3 <1.3.0
        return ver >= base && ver.0 == base.0 && ver.1 == base.1;
    }

    // Exact match
    if let Some(exact) = parse_ver(constraint) {
        return ver == exact;
    }

    true // If we can't parse, assume it's fine
}

/// Detect dependency conflicts in a modpack
#[tauri::command]
pub async fn check_modpack_conflicts(project_path: String) -> Result<Vec<ModConflict>, String> {
    let manifest = load_modpack_manifest(project_path).await?;
    let mut conflicts = Vec::new();

    let enabled_mods: Vec<&ModpackMod> = manifest.mods.iter().filter(|m| m.enabled).collect();

    // Build a map of mod_id -> mod for quick lookup
    let mod_map: HashMap<&str, &ModpackMod> = enabled_mods.iter().map(|m| (m.id.as_str(), *m)).collect();

    // 1. Check for duplicate IDs
    let mut id_counts: HashMap<&str, Vec<&str>> = HashMap::new();
    for m in &enabled_mods {
        id_counts.entry(m.id.as_str()).or_default().push(m.name.as_str());
    }
    for (id, names) in &id_counts {
        if names.len() > 1 {
            conflicts.push(ModConflict {
                kind: "duplicate_id".to_string(),
                mod_id: id.to_string(),
                mod_name: names[0].to_string(),
                details: format!(
                    "Multiple mods share the ID \"{}\": {}",
                    id,
                    names.join(", ")
                ),
                affected_mods: names.iter().map(|n| n.to_string()).collect(),
                suggestion: Some("Rename one of the mods to have a unique ID".to_string()),
            });
        }
    }

    // 2. Check for missing dependencies
    for m in &enabled_mods {
        for dep in &m.dependencies {
            match mod_map.get(dep.mod_id.as_str()) {
                None => {
                    conflicts.push(ModConflict {
                        kind: "missing_dependency".to_string(),
                        mod_id: m.id.clone(),
                        mod_name: m.name.clone(),
                        details: format!(
                            "\"{}\" requires \"{}\" ({}) which is not in the modpack",
                            m.name, dep.mod_id, dep.version_constraint
                        ),
                        affected_mods: vec![m.name.clone()],
                        suggestion: Some(format!("Add \"{}\" to the modpack", dep.mod_id)),
                    });
                }
                Some(dep_mod) => {
                    // 3. Check version constraints
                    if dep.version_constraint != "*"
                        && dep_mod.version != "*"
                        && !satisfies_version(&dep_mod.version, &dep.version_constraint)
                    {
                        conflicts.push(ModConflict {
                            kind: "version_mismatch".to_string(),
                            mod_id: m.id.clone(),
                            mod_name: m.name.clone(),
                            details: format!(
                                "\"{}\" requires \"{}\" {} but version {} is installed",
                                m.name, dep.mod_id, dep.version_constraint, dep_mod.version
                            ),
                            affected_mods: vec![m.name.clone(), dep_mod.name.clone()],
                            suggestion: Some(format!(
                                "Update \"{}\" to a version matching {}",
                                dep_mod.name, dep.version_constraint
                            )),
                        });
                    }
                }
            }
        }
    }

    // 4. Check for environment conflicts
    let server_only: Vec<&str> = enabled_mods
        .iter()
        .filter(|m| m.environment == "server")
        .map(|m| m.name.as_str())
        .collect();
    let client_only: Vec<&str> = enabled_mods
        .iter()
        .filter(|m| m.environment == "client")
        .map(|m| m.name.as_str())
        .collect();

    // Check if a server mod depends on a client mod or vice versa
    for m in &enabled_mods {
        for dep in &m.dependencies {
            if let Some(dep_mod) = mod_map.get(dep.mod_id.as_str()) {
                if m.environment == "server" && dep_mod.environment == "client" {
                    conflicts.push(ModConflict {
                        kind: "environment_conflict".to_string(),
                        mod_id: m.id.clone(),
                        mod_name: m.name.clone(),
                        details: format!(
                            "Server mod \"{}\" depends on client-only mod \"{}\"",
                            m.name, dep_mod.name
                        ),
                        affected_mods: vec![m.name.clone(), dep_mod.name.clone()],
                        suggestion: Some(format!(
                            "Change \"{}\" environment to \"both\" or remove the dependency",
                            dep_mod.name
                        )),
                    });
                } else if m.environment == "client" && dep_mod.environment == "server" {
                    conflicts.push(ModConflict {
                        kind: "environment_conflict".to_string(),
                        mod_id: m.id.clone(),
                        mod_name: m.name.clone(),
                        details: format!(
                            "Client mod \"{}\" depends on server-only mod \"{}\"",
                            m.name, dep_mod.name
                        ),
                        affected_mods: vec![m.name.clone(), dep_mod.name.clone()],
                        suggestion: Some(format!(
                            "Change \"{}\" environment to \"both\" or remove the dependency",
                            dep_mod.name
                        )),
                    });
                }
            }
        }
    }

    // 5. Warn if pack mixes client-only and server-only mods
    if !server_only.is_empty() && !client_only.is_empty() {
        conflicts.push(ModConflict {
            kind: "environment_conflict".to_string(),
            mod_id: String::new(),
            mod_name: String::new(),
            details: format!(
                "Pack mixes client-only mods ({}) with server-only mods ({})",
                client_only.join(", "),
                server_only.join(", ")
            ),
            affected_mods: client_only
                .iter()
                .chain(server_only.iter())
                .map(|s| s.to_string())
                .collect(),
            suggestion: Some("Verify this is intentional; most modpacks target a single environment".to_string()),
        });
    }

    Ok(conflicts)
}

/// Read a mod's config file from the modpack config directory or extract default from JAR
#[tauri::command]
pub async fn read_mod_config(
    project_path: String,
    mod_id: String,
) -> Result<String, String> {
    let config_dir = Path::new(&project_path).join("config");

    // Check for TOML config first, then JSON
    let toml_path = config_dir.join(format!("{}.toml", mod_id));
    if toml_path.exists() {
        return std::fs::read_to_string(&toml_path)
            .map_err(|e| format!("Failed to read config: {}", e));
    }

    let json_path = config_dir.join(format!("{}.json", mod_id));
    if json_path.exists() {
        return std::fs::read_to_string(&json_path)
            .map_err(|e| format!("Failed to read config: {}", e));
    }

    // Try to extract default config from JAR
    let manifest = load_modpack_manifest(project_path.clone()).await?;
    if let Some(m) = manifest.mods.iter().find(|m| m.id == mod_id) {
        if let Some(ref source_path) = m.source_path {
            let jar_path = Path::new(source_path);
            if jar_path.exists() {
                if let Ok(file) = std::fs::File::open(jar_path) {
                    if let Ok(mut archive) = zip::ZipArchive::new(file) {
                        // Look for config templates in the JAR
                        for name in ["config.toml", "default-config.toml", "config.json", "default-config.json"] {
                            let full_name = format!("assets/{}/{}", mod_id, name);
                            if let Ok(mut entry) = archive.by_name(&full_name) {
                                let mut content = String::new();
                                std::io::Read::read_to_string(&mut entry, &mut content)
                                    .map_err(|e| format!("Failed to read from JAR: {}", e))?;
                                return Ok(content);
                            }
                        }
                    }
                }
            }
        }
    }

    // Return a default empty TOML config
    Ok(format!("# Configuration for {}\n# Edit values below and save\n", mod_id))
}

/// Save a mod's config file to the modpack config directory
#[tauri::command]
pub async fn save_mod_config(
    project_path: String,
    mod_id: String,
    content: String,
    format: String,
) -> Result<(), String> {
    let config_dir = Path::new(&project_path).join("config");
    std::fs::create_dir_all(&config_dir)
        .map_err(|e| format!("Failed to create config directory: {}", e))?;

    let ext = if format == "json" { "json" } else { "toml" };
    let config_path = config_dir.join(format!("{}.{}", mod_id, ext));

    std::fs::write(&config_path, &content)
        .map_err(|e| format!("Failed to write config: {}", e))?;

    Ok(())
}

/// Export modpack to .alloypack format (ZIP with metadata)
#[tauri::command]
pub async fn export_modpack(project_path: String, output_path: String) -> Result<String, String> {
    use std::io::Write;

    let project = Path::new(&project_path);
    let manifest = load_modpack_manifest(project_path.clone()).await?;

    let output = Path::new(&output_path);
    let file = std::fs::File::create(output)
        .map_err(|e| format!("Failed to create output file: {}", e))?;

    let mut zip = zip::ZipWriter::new(file);
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Deflated);

    // Write manifest
    let manifest_path = project.join("alloy.pack.toml");
    if manifest_path.exists() {
        let content = std::fs::read_to_string(&manifest_path)
            .map_err(|e| format!("Failed to read manifest: {}", e))?;
        zip.start_file("alloy.pack.toml", options)
            .map_err(|e| format!("ZIP error: {}", e))?;
        zip.write_all(content.as_bytes())
            .map_err(|e| format!("ZIP write error: {}", e))?;
    }

    // Write enabled mod JARs
    let mods_dir = project.join("mods");
    let mut included_count = 0;
    if mods_dir.exists() {
        for m in &manifest.mods {
            if !m.enabled {
                continue;
            }
            if let Some(ref source_path) = m.source_path {
                let jar_path = Path::new(source_path);
                if jar_path.exists() {
                    let jar_name = jar_path.file_name().unwrap_or_default().to_string_lossy();
                    let data = std::fs::read(jar_path)
                        .map_err(|e| format!("Failed to read {}: {}", jar_name, e))?;
                    zip.start_file(format!("mods/{}", jar_name), options)
                        .map_err(|e| format!("ZIP error: {}", e))?;
                    zip.write_all(&data)
                        .map_err(|e| format!("ZIP write error: {}", e))?;
                    included_count += 1;
                }
            }
        }
    }

    // Write pack metadata as JSON for Hub compatibility
    let metadata = serde_json::json!({
        "format": "alloypack",
        "format_version": 1,
        "name": manifest.name,
        "version": manifest.version,
        "minecraft_version": manifest.minecraft_version,
        "alloy_version": manifest.alloy_version,
        "mod_count": manifest.mods.iter().filter(|m| m.enabled).count(),
    });
    zip.start_file("pack.json", options)
        .map_err(|e| format!("ZIP error: {}", e))?;
    zip.write_all(serde_json::to_string_pretty(&metadata).unwrap().as_bytes())
        .map_err(|e| format!("ZIP write error: {}", e))?;

    // Include config overrides if any
    let config_dir = project.join("config");
    if config_dir.exists() {
        fn add_dir_to_zip(
            zip: &mut zip::ZipWriter<std::fs::File>,
            base: &Path,
            dir: &Path,
            prefix: &str,
            options: zip::write::SimpleFileOptions,
        ) -> Result<(), String> {
            let entries = std::fs::read_dir(dir)
                .map_err(|e| format!("Failed to read dir: {}", e))?;
            for entry in entries {
                let entry = entry.map_err(|e| format!("Dir entry error: {}", e))?;
                let path = entry.path();
                let name = path.strip_prefix(base).unwrap_or(&path).to_string_lossy();
                if path.is_dir() {
                    add_dir_to_zip(zip, base, &path, prefix, options)?;
                } else {
                    let data = std::fs::read(&path)
                        .map_err(|e| format!("Failed to read {}: {}", name, e))?;
                    zip.start_file(format!("{}/{}", prefix, name), options)
                        .map_err(|e| format!("ZIP error: {}", e))?;
                    zip.write_all(&data)
                        .map_err(|e| format!("ZIP write error: {}", e))?;
                }
            }
            Ok(())
        }
        add_dir_to_zip(&mut zip, &config_dir, &config_dir, "config", options)?;
    }

    zip.finish().map_err(|e| format!("ZIP finalize error: {}", e))?;

    Ok(format!(
        "Exported {} with {} mods to {}",
        manifest.name,
        included_count,
        output.display()
    ))
}

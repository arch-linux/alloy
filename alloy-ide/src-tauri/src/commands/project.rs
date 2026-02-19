use crate::state::{AppState, ProjectInfo, ProjectType, RecentProject};
use serde::Deserialize;
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::State;

/// Write the current project info to ~/.alloy-ide/current-project.json
/// so the standalone MCP server can pick it up.
fn write_current_project_file(info: &ProjectInfo) {
    let Some(home) = std::env::var_os("HOME")
        .or_else(|| std::env::var_os("USERPROFILE"))
        .map(PathBuf::from)
    else {
        return;
    };
    let dir = home.join(".alloy-ide");
    let _ = fs::create_dir_all(&dir);
    let file = dir.join("current-project.json");
    if let Ok(json) = serde_json::to_string_pretty(info) {
        let _ = fs::write(&file, json);
    }
}

#[tauri::command]
pub async fn open_project(path: String, state: State<'_, Arc<AppState>>) -> Result<ProjectInfo, String> {
    let project_path = Path::new(&path);
    if !project_path.exists() {
        return Err(format!("Path does not exist: {}", path));
    }
    if !project_path.is_dir() {
        return Err(format!("Path is not a directory: {}", path));
    }

    let name = project_path
        .file_name()
        .and_then(|n| n.to_str())
        .unwrap_or("Unknown")
        .to_string();

    let mod_json = project_path.join("alloy.mod.json");
    let pack_toml = project_path.join("alloy.pack.toml");

    let (project_type, environment) = if mod_json.exists() {
        let env = match fs::read_to_string(&mod_json) {
            Ok(content) => {
                serde_json::from_str::<serde_json::Value>(&content)
                    .ok()
                    .and_then(|v| v.get("environment").and_then(|e| e.as_str()).map(String::from))
            }
            Err(_) => None,
        };
        (ProjectType::Mod, env)
    } else if pack_toml.exists() {
        (ProjectType::Modpack, None)
    } else {
        (ProjectType::Unknown, None)
    };

    let info = ProjectInfo {
        name: name.clone(),
        path: path.clone(),
        project_type: project_type.clone(),
        environment,
    };

    // Update current project
    {
        let mut current = state.current_project.lock().map_err(|e| e.to_string())?;
        *current = Some(info.clone());
    }

    // Write shared file for MCP server sync
    write_current_project_file(&info);

    // Add to recent projects
    {
        let mut recents = state.recent_projects.lock().map_err(|e| e.to_string())?;
        recents.retain(|r| r.path != path);
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        recents.insert(
            0,
            RecentProject {
                name,
                path,
                project_type,
                last_opened: now,
            },
        );
        if recents.len() > 20 {
            recents.truncate(20);
        }
    }

    Ok(info)
}

#[tauri::command]
pub async fn get_recent_projects(state: State<'_, Arc<AppState>>) -> Result<Vec<RecentProject>, String> {
    let recents = state.recent_projects.lock().map_err(|e| e.to_string())?;
    Ok(recents.clone())
}

#[derive(Debug, Deserialize)]
pub struct CreateProjectArgs {
    pub name: String,
    pub path: String,
    pub project_type: String,
    pub environment: String,
    pub mod_id: Option<String>,
    pub package_name: Option<String>,
}

#[tauri::command]
pub async fn create_project(args: CreateProjectArgs, state: State<'_, Arc<AppState>>) -> Result<ProjectInfo, String> {
    let project_dir = Path::new(&args.path).join(&args.name);
    if project_dir.exists() {
        return Err(format!("Directory already exists: {}", project_dir.display()));
    }

    fs::create_dir_all(&project_dir).map_err(|e| format!("Failed to create directory: {}", e))?;

    match args.project_type.as_str() {
        "mod" => scaffold_mod_project(&project_dir, &args)?,
        "modpack" => scaffold_modpack_project(&project_dir, &args)?,
        _ => return Err(format!("Unknown project type: {}", args.project_type)),
    }

    // Open the newly created project
    let path_str = project_dir.to_string_lossy().to_string();
    let info = ProjectInfo {
        name: args.name.clone(),
        path: path_str.clone(),
        project_type: match args.project_type.as_str() {
            "mod" => ProjectType::Mod,
            "modpack" => ProjectType::Modpack,
            _ => ProjectType::Unknown,
        },
        environment: Some(args.environment.clone()),
    };

    {
        let mut current = state.current_project.lock().map_err(|e| e.to_string())?;
        *current = Some(info.clone());
    }

    // Write shared file for MCP server sync
    write_current_project_file(&info);

    {
        let mut recents = state.recent_projects.lock().map_err(|e| e.to_string())?;
        recents.retain(|r| r.path != path_str);
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        recents.insert(0, RecentProject {
            name: args.name,
            path: path_str,
            project_type: match args.project_type.as_str() {
                "mod" => ProjectType::Mod,
                "modpack" => ProjectType::Modpack,
                _ => ProjectType::Unknown,
            },
            last_opened: now,
        });
        if recents.len() > 20 {
            recents.truncate(20);
        }
    }

    Ok(info)
}

fn scaffold_mod_project(dir: &Path, args: &CreateProjectArgs) -> Result<(), String> {
    let mod_id = args.mod_id.as_deref().unwrap_or("mymod");
    let pkg = args.package_name.as_deref().unwrap_or("com.example");
    let pkg_path = pkg.replace('.', "/");
    let class_name = to_pascal_case(&args.name);

    // alloy.mod.json
    let mod_json = format!(
        r#"{{
  "id": "{}",
  "name": "{}",
  "version": "1.0.0",
  "entrypoint": "{}.{}.{}",
  "environment": "{}"
}}"#,
        mod_id, args.name, pkg, mod_id, class_name, args.environment
    );
    fs::write(dir.join("alloy.mod.json"), mod_json)
        .map_err(|e| format!("Failed to write alloy.mod.json: {}", e))?;

    // build.gradle.kts
    let build_gradle = format!(
        r#"plugins {{
    java
}}

group = "{}.{}"
version = "1.0.0"

repositories {{
    mavenCentral()
}}

java {{
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}}

tasks.withType<JavaCompile> {{
    options.release.set(21)
    options.encoding = "UTF-8"
}}

dependencies {{
    // Alloy API — provided at runtime by the loader
    compileOnly(files("libs/alloy-api.jar"))
}}
"#,
        pkg, mod_id
    );
    fs::write(dir.join("build.gradle.kts"), build_gradle)
        .map_err(|e| format!("Failed to write build.gradle.kts: {}", e))?;

    // settings.gradle.kts
    let settings = format!("rootProject.name = \"{}\"", mod_id);
    fs::write(dir.join("settings.gradle.kts"), settings)
        .map_err(|e| format!("Failed to write settings.gradle.kts: {}", e))?;

    // .gitignore
    let gitignore = "build/\n.gradle/\nlibs/\nout/\n*.class\n.idea/\n*.iml\n";
    fs::write(dir.join(".gitignore"), gitignore)
        .map_err(|e| format!("Failed to write .gitignore: {}", e))?;

    // Source directories
    let src_dir = dir.join("src/main/java").join(&pkg_path).join(mod_id);
    fs::create_dir_all(&src_dir)
        .map_err(|e| format!("Failed to create source directory: {}", e))?;

    // Resources directory
    let res_dir = dir.join("src/main/resources/assets").join(mod_id).join("textures");
    fs::create_dir_all(&res_dir)
        .map_err(|e| format!("Failed to create resource directory: {}", e))?;

    // Main class
    let entry_class = match args.environment.as_str() {
        "server" => format!(
            r#"package {pkg}.{mod_id};

import net.alloymc.loader.api.ModInitializer;
import net.alloymc.api.AlloyAPI;
import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.Listener;
import net.alloymc.api.event.player.PlayerJoinEvent;

public class {class_name} implements ModInitializer, Listener {{

    @Override
    public void onInitialize() {{
        AlloyAPI.getEventBus().register(this);
    }}

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {{
        event.getPlayer().sendMessage("Hello from {name}!");
    }}
}}
"#,
            pkg = pkg,
            mod_id = mod_id,
            class_name = class_name,
            name = args.name
        ),
        "client" => format!(
            r#"package {pkg}.{mod_id};

import net.alloymc.loader.api.ModInitializer;

public class {class_name} implements ModInitializer {{

    @Override
    public void onInitialize() {{
        // Client-side initialization
        // Register screens, renderers, HUD elements, etc.
    }}
}}
"#,
            pkg = pkg,
            mod_id = mod_id,
            class_name = class_name
        ),
        _ => format!(
            r#"package {pkg}.{mod_id};

import net.alloymc.loader.api.ModInitializer;
import net.alloymc.api.AlloyAPI;
import net.alloymc.api.event.EventHandler;
import net.alloymc.api.event.Listener;
import net.alloymc.api.event.player.PlayerJoinEvent;

public class {class_name} implements ModInitializer, Listener {{

    @Override
    public void onInitialize() {{
        AlloyAPI.getEventBus().register(this);
    }}

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {{
        event.getPlayer().sendMessage("Hello from {name}!");
    }}
}}
"#,
            pkg = pkg,
            mod_id = mod_id,
            class_name = class_name,
            name = args.name
        ),
    };

    fs::write(src_dir.join(format!("{}.java", class_name)), entry_class)
        .map_err(|e| format!("Failed to write entry class: {}", e))?;

    // libs/ directory (placeholder)
    fs::create_dir_all(dir.join("libs"))
        .map_err(|e| format!("Failed to create libs directory: {}", e))?;

    Ok(())
}

fn scaffold_modpack_project(dir: &Path, args: &CreateProjectArgs) -> Result<(), String> {
    let pack_toml = format!(
        r#"[pack]
name = "{}"
version = "1.0.0"
description = ""
authors = []

[alloy]
version = "0.1.0"
minecraft = "1.21.11"

[mods]
# Add mods here:
# my-mod = {{ path = "../my-mod" }}
# some-mod = {{ git = "https://github.com/user/some-mod.git", tag = "v1.0.0" }}
"#,
        args.name
    );
    fs::write(dir.join("alloy.pack.toml"), pack_toml)
        .map_err(|e| format!("Failed to write alloy.pack.toml: {}", e))?;

    // mods/ directory for local JAR imports
    fs::create_dir_all(dir.join("mods"))
        .map_err(|e| format!("Failed to create mods directory: {}", e))?;

    // config/ directory for mod configs
    fs::create_dir_all(dir.join("config"))
        .map_err(|e| format!("Failed to create config directory: {}", e))?;

    // .gitignore
    let gitignore = "build/\n*.alloypack\n";
    fs::write(dir.join(".gitignore"), gitignore)
        .map_err(|e| format!("Failed to write .gitignore: {}", e))?;

    Ok(())
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

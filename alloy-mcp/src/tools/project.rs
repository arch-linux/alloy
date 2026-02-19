use crate::state::{load_from_shared_file, write_current_project_file, ProjectInfo, ProjectState, ProjectType};
use crate::types::{ToolDefinition, ToolResult};
use serde_json::{json, Value};
use std::path::Path;

/// Return tool definitions for all project tools.
pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "project_create".into(),
            description: "Create a new Alloy mod or modpack project with full scaffolding. \
                Generates manifest, build files, source directories, and entry point class."
                .into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Absolute path where the project directory will be created"
                    },
                    "name": {
                        "type": "string",
                        "description": "Human-readable project name (e.g. 'My Cool Mod')"
                    },
                    "mod_id": {
                        "type": "string",
                        "description": "Machine-readable mod identifier (e.g. 'mycoolmod'). Lowercase, no spaces."
                    },
                    "project_type": {
                        "type": "string",
                        "enum": ["mod", "modpack"],
                        "description": "Whether to create a mod or modpack project"
                    },
                    "environment": {
                        "type": "string",
                        "enum": ["client", "server", "both"],
                        "description": "Target environment for the mod (default: 'both'). Ignored for modpacks."
                    }
                },
                "required": ["path", "name", "mod_id", "project_type"]
            }),
        },
        ToolDefinition {
            name: "project_open".into(),
            description: "Set the active project directory. Reads the project manifest and \
                updates IDE state so all other tools operate relative to this project."
                .into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Absolute path to an existing Alloy project directory"
                    }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "project_info".into(),
            description: "Get information about the currently open project, including name, \
                path, type, and environment."
                .into(),
            input_schema: json!({
                "type": "object",
                "properties": {}
            }),
        },
        ToolDefinition {
            name: "project_list_recent".into(),
            description: "List recently opened projects from the IDE history file at \
                ~/.alloy-ide/recent-projects.json."
                .into(),
            input_schema: json!({
                "type": "object",
                "properties": {}
            }),
        },
    ]
}

/// Dispatch execution to the appropriate project tool handler.
pub async fn execute(name: &str, params: Value, state: &ProjectState) -> ToolResult {
    match name {
        "project_create" => handle_create(params, state).await,
        "project_open" => handle_open(params, state).await,
        "project_info" => handle_info(state).await,
        "project_list_recent" => handle_list_recent().await,
        _ => ToolResult::error(format!("Unknown project tool: {}", name)),
    }
}

// ---------------------------------------------------------------------------
// project_create
// ---------------------------------------------------------------------------

async fn handle_create(params: Value, state: &ProjectState) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };
    let name = match params.get("name").and_then(|v| v.as_str()) {
        Some(n) => n,
        None => return ToolResult::error("Missing required parameter: name"),
    };
    let mod_id = match params.get("mod_id").and_then(|v| v.as_str()) {
        Some(id) => id,
        None => return ToolResult::error("Missing required parameter: mod_id"),
    };
    let project_type = match params.get("project_type").and_then(|v| v.as_str()) {
        Some(t) => t,
        None => return ToolResult::error("Missing required parameter: project_type"),
    };
    let environment = params
        .get("environment")
        .and_then(|v| v.as_str())
        .unwrap_or("both");

    let project_dir = Path::new(path);

    if project_dir.exists() {
        return ToolResult::error(format!(
            "Directory already exists: {}. Choose a different path or delete it first.",
            path
        ));
    }

    match project_type {
        "mod" => create_mod_project(project_dir, name, mod_id, environment, state).await,
        "modpack" => create_modpack_project(project_dir, name, mod_id, state).await,
        other => ToolResult::error(format!(
            "Invalid project_type '{}'. Must be 'mod' or 'modpack'.",
            other
        )),
    }
}

async fn create_mod_project(
    project_dir: &Path,
    name: &str,
    mod_id: &str,
    environment: &str,
    state: &ProjectState,
) -> ToolResult {
    let mut created_files: Vec<String> = Vec::new();

    // Build the package path from the mod_id: net.alloymc.<mod_id>
    let package = format!("net.alloymc.{}", mod_id);
    let package_path = package.replace('.', "/");
    let entry_point = format!("{}.ModInit", package);

    // Create directory structure
    let src_java_dir = project_dir.join("src/main/java").join(&package_path);
    let src_resources_dir = project_dir.join("src/main/resources");

    if let Err(e) = std::fs::create_dir_all(&src_java_dir) {
        return ToolResult::error(format!("Failed to create source directories: {}", e));
    }
    if let Err(e) = std::fs::create_dir_all(&src_resources_dir) {
        return ToolResult::error(format!("Failed to create resources directory: {}", e));
    }

    // 1. alloy.mod.json
    let mod_json = json!({
        "name": name,
        "mod_id": mod_id,
        "version": "0.1.0",
        "environment": environment,
        "entry_point": entry_point,
        "description": "",
        "authors": [],
        "license": "MIT"
    });
    let mod_json_path = project_dir.join("alloy.mod.json");
    match std::fs::write(&mod_json_path, serde_json::to_string_pretty(&mod_json).unwrap()) {
        Ok(_) => created_files.push(mod_json_path.display().to_string()),
        Err(e) => {
            return ToolResult::error(format!("Failed to write alloy.mod.json: {}", e));
        }
    }

    // 2. build.gradle.kts
    let build_gradle = format!(
        r#"plugins {{
    java
}}

group = "{package}"
version = "0.1.0"

java {{
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}}

repositories {{
    mavenCentral()
    maven {{
        name = "alloy"
        url = uri("https://maven.alloymc.net/releases")
    }}
}}

dependencies {{
    compileOnly("net.alloymc:alloy-api:+")
}}

tasks.withType<JavaCompile> {{
    options.release.set(21)
}}
"#
    );
    let build_gradle_path = project_dir.join("build.gradle.kts");
    match std::fs::write(&build_gradle_path, &build_gradle) {
        Ok(_) => created_files.push(build_gradle_path.display().to_string()),
        Err(e) => {
            return ToolResult::error(format!("Failed to write build.gradle.kts: {}", e));
        }
    }

    // 3. settings.gradle.kts
    let settings_gradle = format!("rootProject.name = \"{}\"\n", mod_id);
    let settings_gradle_path = project_dir.join("settings.gradle.kts");
    match std::fs::write(&settings_gradle_path, &settings_gradle) {
        Ok(_) => created_files.push(settings_gradle_path.display().to_string()),
        Err(e) => {
            return ToolResult::error(format!("Failed to write settings.gradle.kts: {}", e));
        }
    }

    // 4. ModInit.java — entry point class
    let mod_init_java = format!(
        r#"package {package};

import net.alloymc.api.ModInitializer;

/**
 * Entry point for the {name} mod.
 */
public class ModInit implements ModInitializer {{

    @Override
    public void onInitialize() {{
        System.out.println("[{name}] Mod initialized!");
    }}
}}
"#
    );
    let mod_init_path = src_java_dir.join("ModInit.java");
    match std::fs::write(&mod_init_path, &mod_init_java) {
        Ok(_) => created_files.push(mod_init_path.display().to_string()),
        Err(e) => {
            return ToolResult::error(format!("Failed to write ModInit.java: {}", e));
        }
    }

    // 5. .gitignore
    let gitignore = "build/\n.gradle/\n*.class\n*.jar\nout/\n";
    let gitignore_path = project_dir.join(".gitignore");
    match std::fs::write(&gitignore_path, gitignore) {
        Ok(_) => created_files.push(gitignore_path.display().to_string()),
        Err(e) => {
            return ToolResult::error(format!("Failed to write .gitignore: {}", e));
        }
    }

    // Update state to point at the new project
    {
        let info = ProjectInfo {
            name: name.to_string(),
            path: project_dir.display().to_string(),
            project_type: ProjectType::Mod,
            environment: Some(environment.to_string()),
        };
        if let Ok(mut current) = state.current_project.lock() {
            *current = Some(info.clone());
        }
        write_current_project_file(&info);
    }

    ToolResult::json(&json!({
        "status": "created",
        "project_type": "mod",
        "name": name,
        "mod_id": mod_id,
        "environment": environment,
        "path": project_dir.display().to_string(),
        "created_files": created_files
    }))
}

async fn create_modpack_project(
    project_dir: &Path,
    name: &str,
    mod_id: &str,
    state: &ProjectState,
) -> ToolResult {
    let mut created_files: Vec<String> = Vec::new();

    // Create directories
    let mods_dir = project_dir.join("mods");
    if let Err(e) = std::fs::create_dir_all(&mods_dir) {
        return ToolResult::error(format!("Failed to create project directories: {}", e));
    }

    // 1. alloy.pack.toml
    let pack_toml = format!(
        r#"[pack]
name = "{name}"
pack_id = "{mod_id}"
version = "0.1.0"
description = ""
authors = []

[minecraft]
version = "1.21"

[alloy]
version = "*"

[mods]
# Add mods here:
# my_mod = {{ source = "local", path = "mods/my_mod.jar" }}
"#
    );
    let pack_toml_path = project_dir.join("alloy.pack.toml");
    match std::fs::write(&pack_toml_path, &pack_toml) {
        Ok(_) => created_files.push(pack_toml_path.display().to_string()),
        Err(e) => {
            return ToolResult::error(format!("Failed to write alloy.pack.toml: {}", e));
        }
    }

    // 2. .gitignore
    let gitignore = "mods/*.jar\n";
    let gitignore_path = project_dir.join(".gitignore");
    match std::fs::write(&gitignore_path, gitignore) {
        Ok(_) => created_files.push(gitignore_path.display().to_string()),
        Err(e) => {
            return ToolResult::error(format!("Failed to write .gitignore: {}", e));
        }
    }

    // Update state
    {
        let info = ProjectInfo {
            name: name.to_string(),
            path: project_dir.display().to_string(),
            project_type: ProjectType::Modpack,
            environment: None,
        };
        if let Ok(mut current) = state.current_project.lock() {
            *current = Some(info.clone());
        }
        write_current_project_file(&info);
    }

    ToolResult::json(&json!({
        "status": "created",
        "project_type": "modpack",
        "name": name,
        "pack_id": mod_id,
        "path": project_dir.display().to_string(),
        "created_files": created_files
    }))
}

// ---------------------------------------------------------------------------
// project_open
// ---------------------------------------------------------------------------

async fn handle_open(params: Value, state: &ProjectState) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };

    let project_dir = Path::new(path);
    if !project_dir.exists() {
        return ToolResult::error(format!("Directory does not exist: {}", path));
    }
    if !project_dir.is_dir() {
        return ToolResult::error(format!("Path is not a directory: {}", path));
    }

    // Detect project type by reading manifests
    let mod_json_path = project_dir.join("alloy.mod.json");
    let pack_toml_path = project_dir.join("alloy.pack.toml");

    let (project_type, environment, name) = if mod_json_path.exists() {
        match std::fs::read_to_string(&mod_json_path) {
            Ok(content) => {
                let parsed: Value = serde_json::from_str(&content).unwrap_or(json!({}));
                let env = parsed
                    .get("environment")
                    .and_then(|e| e.as_str())
                    .map(String::from);
                let n = parsed
                    .get("name")
                    .and_then(|e| e.as_str())
                    .unwrap_or("Unknown")
                    .to_string();
                (ProjectType::Mod, env, n)
            }
            Err(e) => {
                return ToolResult::error(format!("Failed to read alloy.mod.json: {}", e));
            }
        }
    } else if pack_toml_path.exists() {
        match std::fs::read_to_string(&pack_toml_path) {
            Ok(content) => {
                let parsed: toml::Value = content.parse().unwrap_or(toml::Value::Table(Default::default()));
                let n = parsed
                    .get("pack")
                    .and_then(|p| p.get("name"))
                    .and_then(|n| n.as_str())
                    .unwrap_or("Unknown")
                    .to_string();
                (ProjectType::Modpack, None, n)
            }
            Err(e) => {
                return ToolResult::error(format!("Failed to read alloy.pack.toml: {}", e));
            }
        }
    } else {
        // No recognized manifest — open as unknown
        let n = project_dir
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("Unknown")
            .to_string();
        (ProjectType::Unknown, None, n)
    };

    let info = ProjectInfo {
        name: name.clone(),
        path: path.to_string(),
        project_type: project_type.clone(),
        environment: environment.clone(),
    };

    // Update state
    if let Ok(mut current) = state.current_project.lock() {
        *current = Some(info.clone());
    }

    // Write shared file for IDE sync
    write_current_project_file(&info);

    // Append to recent projects
    append_recent_project(&info);

    ToolResult::json(&json!({
        "status": "opened",
        "name": name,
        "path": path,
        "project_type": project_type,
        "environment": environment
    }))
}

/// Append a project to the recent projects list (~/.alloy-ide/recent-projects.json).
fn append_recent_project(info: &ProjectInfo) {
    let home = match dirs_home() {
        Some(h) => h,
        None => return,
    };
    let recent_dir = home.join(".alloy-ide");
    let recent_file = recent_dir.join("recent-projects.json");

    let _ = std::fs::create_dir_all(&recent_dir);

    let mut entries: Vec<Value> = if recent_file.exists() {
        std::fs::read_to_string(&recent_file)
            .ok()
            .and_then(|content| serde_json::from_str(&content).ok())
            .unwrap_or_default()
    } else {
        Vec::new()
    };

    // Remove any existing entry with the same path
    entries.retain(|e| e.get("path").and_then(|p| p.as_str()) != Some(&info.path));

    // Insert at front
    entries.insert(
        0,
        json!({
            "name": info.name,
            "path": info.path,
            "project_type": info.project_type,
            "opened_at": chrono_now_iso()
        }),
    );

    // Keep at most 20 entries
    entries.truncate(20);

    let _ = std::fs::write(&recent_file, serde_json::to_string_pretty(&entries).unwrap_or_default());
}

/// Simple ISO-8601 timestamp without pulling in chrono.
fn chrono_now_iso() -> String {
    let now = std::time::SystemTime::now();
    let duration = now
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default();
    format!("unix:{}", duration.as_secs())
}

/// Return the user's home directory.
fn dirs_home() -> Option<std::path::PathBuf> {
    std::env::var_os("HOME")
        .or_else(|| std::env::var_os("USERPROFILE"))
        .map(std::path::PathBuf::from)
}

// ---------------------------------------------------------------------------
// project_info
// ---------------------------------------------------------------------------

async fn handle_info(state: &ProjectState) -> ToolResult {
    // Always re-read the shared file to pick up IDE-side project changes.
    // The IDE writes to ~/.alloy-ide/current-project.json whenever the user
    // opens a different project, so we must check it on every call rather than
    // relying on potentially stale in-memory state.
    let info = match load_from_shared_file() {
        Some(info) => {
            // Update in-memory state so other tools stay in sync
            if let Ok(mut current) = state.current_project.lock() {
                *current = Some(info.clone());
            }
            info
        }
        None => {
            // Shared file missing or invalid — fall back to in-memory state
            match state.current_project.lock() {
                Ok(guard) => match guard.clone() {
                    Some(info) => info,
                    None => {
                        return ToolResult::text(
                            "No project is currently open. Use project_open or project_create first.",
                        );
                    }
                },
                Err(e) => return ToolResult::error(format!("Failed to read state: {}", e)),
            }
        }
    };

    ToolResult::json(&json!({
        "name": info.name,
        "path": info.path,
        "project_type": info.project_type,
        "environment": info.environment
    }))
}

// ---------------------------------------------------------------------------
// project_list_recent
// ---------------------------------------------------------------------------

async fn handle_list_recent() -> ToolResult {
    let home = match dirs_home() {
        Some(h) => h,
        None => {
            return ToolResult::error(
                "Could not determine home directory. Set HOME or USERPROFILE environment variable.",
            );
        }
    };

    let recent_file = home.join(".alloy-ide/recent-projects.json");

    if !recent_file.exists() {
        return ToolResult::json(&json!({
            "recent_projects": [],
            "message": "No recent projects found"
        }));
    }

    match std::fs::read_to_string(&recent_file) {
        Ok(content) => {
            let entries: Vec<Value> = serde_json::from_str(&content).unwrap_or_default();

            // Annotate each entry with whether the directory still exists
            let annotated: Vec<Value> = entries
                .into_iter()
                .map(|mut entry| {
                    let exists = entry
                        .get("path")
                        .and_then(|p| p.as_str())
                        .map(|p| Path::new(p).exists())
                        .unwrap_or(false);
                    if let Value::Object(ref mut map) = entry {
                        map.insert("exists".into(), json!(exists));
                    }
                    entry
                })
                .collect();

            ToolResult::json(&json!({
                "recent_projects": annotated
            }))
        }
        Err(e) => ToolResult::error(format!("Failed to read recent projects file: {}", e)),
    }
}

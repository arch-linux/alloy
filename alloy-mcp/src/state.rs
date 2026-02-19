use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::Mutex;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct ProjectInfo {
    pub name: String,
    pub path: String,
    pub project_type: ProjectType,
    pub environment: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
#[serde(rename_all = "snake_case")]
pub enum ProjectType {
    Mod,
    Modpack,
    Unknown,
}

/// Read the shared project file written by the IDE at ~/.alloy-ide/current-project.json.
/// Returns `Some(info)` only if the file exists, parses correctly, and the path on disk is valid.
pub(crate) fn load_from_shared_file() -> Option<ProjectInfo> {
    let home = std::env::var_os("HOME")
        .or_else(|| std::env::var_os("USERPROFILE"))
        .map(PathBuf::from)?;
    let file = home.join(".alloy-ide/current-project.json");
    let content = std::fs::read_to_string(&file).ok()?;
    let info: ProjectInfo = serde_json::from_str(&content).ok()?;
    // Validate the path still exists on disk
    if std::path::Path::new(&info.path).is_dir() {
        Some(info)
    } else {
        None
    }
}

/// Write project info to ~/.alloy-ide/current-project.json for bidirectional sync.
pub(crate) fn write_current_project_file(info: &ProjectInfo) {
    let Some(home) = std::env::var_os("HOME")
        .or_else(|| std::env::var_os("USERPROFILE"))
        .map(PathBuf::from)
    else {
        return;
    };
    let dir = home.join(".alloy-ide");
    let _ = std::fs::create_dir_all(&dir);
    let file = dir.join("current-project.json");
    if let Ok(json) = serde_json::to_string_pretty(info) {
        let _ = std::fs::write(&file, json);
    }
}

pub struct ProjectState {
    pub current_project: Mutex<Option<ProjectInfo>>,
    pub terminal_output: Mutex<Vec<String>>,
    pub build_errors: Mutex<Vec<String>>,
}

impl ProjectState {
    pub fn new() -> Self {
        // Try to load from the shared IDE file as a starting point
        let initial = load_from_shared_file();
        Self {
            current_project: Mutex::new(initial),
            terminal_output: Mutex::new(Vec::new()),
            build_errors: Mutex::new(Vec::new()),
        }
    }

    pub fn with_project(project_path: &str) -> Self {
        let state = Self::new();

        let path = std::path::Path::new(project_path);
        if path.exists() && path.is_dir() {
            let mod_json = path.join("alloy.mod.json");
            let pack_toml = path.join("alloy.pack.toml");

            // Only override the shared-file state when the provided path has an actual manifest
            let has_manifest = mod_json.exists() || pack_toml.exists();
            if has_manifest {
                let name = path
                    .file_name()
                    .and_then(|n| n.to_str())
                    .unwrap_or("Unknown")
                    .to_string();

                let (project_type, environment) = if mod_json.exists() {
                    let env = std::fs::read_to_string(&mod_json)
                        .ok()
                        .and_then(|content| {
                            serde_json::from_str::<serde_json::Value>(&content)
                                .ok()
                                .and_then(|v| {
                                    v.get("environment")
                                        .and_then(|e| e.as_str())
                                        .map(String::from)
                                })
                        });
                    (ProjectType::Mod, env)
                } else {
                    (ProjectType::Modpack, None)
                };

                let info = ProjectInfo {
                    name,
                    path: project_path.to_string(),
                    project_type,
                    environment,
                };

                *state.current_project.lock().unwrap() = Some(info);
            }
            // If no manifest found, keep whatever new() loaded from the shared file
        }

        state
    }

    /// Get the current project path, or an error message.
    pub fn project_path(&self) -> Result<String, String> {
        let current = self.current_project.lock().map_err(|e| e.to_string())?;
        match current.as_ref() {
            Some(info) => Ok(info.path.clone()),
            None => Err("No project is currently open".to_string()),
        }
    }
}

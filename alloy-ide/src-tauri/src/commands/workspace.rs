use serde::{Deserialize, Serialize};
use std::fs;
use std::path::Path;

#[derive(Debug, Serialize, Deserialize)]
pub struct WorkspaceState {
    /// Paths of open files in tab order
    pub open_files: Vec<String>,
    /// Path of the active (focused) file
    pub active_file: Option<String>,
    /// Which sidebar panel is shown
    pub sidebar_panel: String,
    /// Whether sidebar is visible
    pub sidebar_visible: bool,
    /// Sidebar width in pixels
    pub sidebar_width: f64,
    /// Which bottom panel tab is active
    pub bottom_panel: String,
    /// Whether bottom panel is visible
    pub bottom_panel_visible: bool,
    /// Bottom panel height in pixels
    pub bottom_panel_height: f64,
}

#[tauri::command]
pub async fn save_workspace_state(project_path: String, state: WorkspaceState) -> Result<(), String> {
    let ide_dir = Path::new(&project_path).join(".alloy-ide");
    fs::create_dir_all(&ide_dir)
        .map_err(|e| format!("Failed to create .alloy-ide dir: {}", e))?;

    let state_path = ide_dir.join("workspace.json");
    let json = serde_json::to_string_pretty(&state)
        .map_err(|e| format!("Failed to serialize state: {}", e))?;

    fs::write(&state_path, json)
        .map_err(|e| format!("Failed to write workspace state: {}", e))?;

    Ok(())
}

#[tauri::command]
pub async fn load_workspace_state(project_path: String) -> Result<Option<WorkspaceState>, String> {
    let state_path = Path::new(&project_path).join(".alloy-ide/workspace.json");

    if !state_path.exists() {
        return Ok(None);
    }

    let content = fs::read_to_string(&state_path)
        .map_err(|e| format!("Failed to read workspace state: {}", e))?;

    let state: WorkspaceState = serde_json::from_str(&content)
        .map_err(|e| format!("Failed to parse workspace state: {}", e))?;

    Ok(Some(state))
}

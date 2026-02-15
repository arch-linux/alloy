use crate::state::{AppState, LauncherSettings};
use tauri::State;

#[tauri::command]
pub async fn get_settings(state: State<'_, AppState>) -> Result<LauncherSettings, String> {
    let settings = state.settings.lock().unwrap();
    Ok(settings.clone())
}

#[tauri::command]
pub async fn update_settings(
    state: State<'_, AppState>,
    settings: LauncherSettings,
) -> Result<(), String> {
    let mut current = state.settings.lock().unwrap();
    *current = settings;
    Ok(())
}

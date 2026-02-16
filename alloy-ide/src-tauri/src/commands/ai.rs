use std::sync::Arc;
use tauri::{AppHandle, State};

use crate::mcp::claude::ClaudeClient;
use crate::mcp::types::{AiConfig, ChatMessage};
use crate::state::AppState;

#[tauri::command]
pub async fn ai_send_message(
    message: String,
    state: State<'_, Arc<AppState>>,
    app_handle: AppHandle,
) -> Result<ChatMessage, String> {
    let state_arc = state.inner().clone();
    let client = ClaudeClient::new();
    client
        .send_message(&message, &state_arc, &app_handle)
        .await
}

#[tauri::command]
pub async fn ai_get_history(state: State<'_, Arc<AppState>>) -> Result<Vec<ChatMessage>, String> {
    let history = state.chat_history.lock().map_err(|e| e.to_string())?;
    Ok(history.clone())
}

#[tauri::command]
pub async fn ai_clear_history(state: State<'_, Arc<AppState>>) -> Result<(), String> {
    let mut history = state.chat_history.lock().map_err(|e| e.to_string())?;
    history.clear();
    Ok(())
}

#[tauri::command]
pub async fn ai_set_config(config: AiConfig, state: State<'_, Arc<AppState>>) -> Result<(), String> {
    let mut ai_config = state.ai_config.lock().map_err(|e| e.to_string())?;
    *ai_config = config;
    Ok(())
}

#[tauri::command]
pub async fn ai_get_config(state: State<'_, Arc<AppState>>) -> Result<AiConfig, String> {
    let config = state.ai_config.lock().map_err(|e| e.to_string())?;
    Ok(config.clone())
}

#[tauri::command]
pub async fn ai_update_editor_state(
    open_files: Vec<String>,
    selection: Option<String>,
    state: State<'_, Arc<AppState>>,
) -> Result<(), String> {
    if let Ok(mut files) = state.open_editor_files.lock() {
        *files = open_files;
    }
    if let Ok(mut sel) = state.editor_selection.lock() {
        *sel = selection;
    }
    Ok(())
}

#[tauri::command]
pub async fn ai_get_pending_actions(state: State<'_, Arc<AppState>>) -> Result<Vec<serde_json::Value>, String> {
    let mut actions = state.pending_editor_actions.lock().map_err(|e| e.to_string())?;
    let result = actions.clone();
    actions.clear();
    Ok(result)
}

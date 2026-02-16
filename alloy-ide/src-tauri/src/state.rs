use serde::{Deserialize, Serialize};
use std::sync::Mutex;

use crate::mcp::types::{AiConfig, ChatMessage};

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

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct RecentProject {
    pub name: String,
    pub path: String,
    pub project_type: ProjectType,
    pub last_opened: u64,
}

pub struct AppState {
    // Existing state
    pub current_project: Mutex<Option<ProjectInfo>>,
    pub recent_projects: Mutex<Vec<RecentProject>>,

    // Editor state (synced from frontend for tool access)
    pub open_editor_files: Mutex<Vec<String>>,
    pub editor_selection: Mutex<Option<String>>,
    pub pending_editor_actions: Mutex<Vec<serde_json::Value>>,

    // Build state
    pub build_errors: Mutex<Vec<serde_json::Value>>,

    // Terminal state
    pub terminal_output: Mutex<Vec<String>>,

    // AI state
    pub ai_config: Mutex<AiConfig>,
    pub chat_history: Mutex<Vec<ChatMessage>>,
}

impl AppState {
    pub fn new() -> Self {
        Self {
            current_project: Mutex::new(None),
            recent_projects: Mutex::new(Vec::new()),
            open_editor_files: Mutex::new(Vec::new()),
            editor_selection: Mutex::new(None),
            pending_editor_actions: Mutex::new(Vec::new()),
            build_errors: Mutex::new(Vec::new()),
            terminal_output: Mutex::new(Vec::new()),
            ai_config: Mutex::new(AiConfig::default()),
            chat_history: Mutex::new(Vec::new()),
        }
    }
}

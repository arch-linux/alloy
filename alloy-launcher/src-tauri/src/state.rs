use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::Duration;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct MinecraftProfile {
    pub username: String,
    pub uuid: String,
    pub skin_url: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct AuthTokens {
    pub mc_access_token: String,
    pub ms_refresh_token: String,
    pub profile: MinecraftProfile,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct LauncherSettings {
    pub memory_mb: u32,
    pub java_path: Option<String>,
    pub jvm_args: String,
    pub client_id: String,
    pub cache_dir: Option<String>,
    pub minecraft_version: String,
}

impl Default for LauncherSettings {
    fn default() -> Self {
        Self {
            memory_mb: 2048,
            java_path: None,
            jvm_args: String::new(),
            client_id: crate::DEFAULT_CLIENT_ID.to_string(),
            cache_dir: None,
            minecraft_version: "1.21.11".to_string(),
        }
    }
}

pub struct AppState {
    pub http_client: Client,
    pub base_dir: PathBuf,
    pub auth: Mutex<Option<AuthTokens>>,
    pub settings: Mutex<LauncherSettings>,
}

impl AppState {
    pub fn new() -> Self {
        let base_dir = dirs_base_dir();
        Self {
            http_client: Client::builder()
                .user_agent("AlloyLauncher/0.1.0")
                .timeout(Duration::from_secs(30))
                .connect_timeout(Duration::from_secs(10))
                .build()
                .expect("failed to build HTTP client"),
            base_dir,
            auth: Mutex::new(None),
            settings: Mutex::new(LauncherSettings::default()),
        }
    }

    pub fn cache_dir(&self) -> PathBuf {
        let settings = self.settings.lock().unwrap();
        if let Some(ref dir) = settings.cache_dir {
            PathBuf::from(dir)
        } else {
            self.base_dir.join("cache")
        }
    }

    pub fn run_dir(&self) -> PathBuf {
        self.base_dir.join("run")
    }
}

fn dirs_base_dir() -> PathBuf {
    if let Ok(dir) = std::env::var("ALLOY_HOME") {
        return PathBuf::from(dir);
    }

    #[cfg(target_os = "macos")]
    {
        if let Some(home) = dirs_home() {
            return home.join("Library").join("Application Support").join("alloy");
        }
    }

    #[cfg(target_os = "windows")]
    {
        if let Ok(appdata) = std::env::var("APPDATA") {
            return PathBuf::from(appdata).join("alloy");
        }
    }

    #[cfg(target_os = "linux")]
    {
        if let Some(home) = dirs_home() {
            return home.join(".alloy");
        }
    }

    PathBuf::from(".alloy")
}

fn dirs_home() -> Option<PathBuf> {
    std::env::var("HOME").ok().map(PathBuf::from)
}

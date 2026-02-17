use notify::{Config, EventKind, RecommendedWatcher, RecursiveMode, Watcher};
use serde::Serialize;
use std::collections::HashSet;
use std::path::PathBuf;
use std::sync::Mutex;
use tauri::{AppHandle, Emitter, Manager};

#[derive(Debug, Serialize, Clone)]
pub struct FileChangedEvent {
    pub path: String,
    pub kind: String,
}

pub struct WatcherState {
    watcher: Mutex<Option<RecommendedWatcher>>,
    watched_paths: Mutex<HashSet<String>>,
}

impl WatcherState {
    pub fn new() -> Self {
        Self {
            watcher: Mutex::new(None),
            watched_paths: Mutex::new(HashSet::new()),
        }
    }
}

#[tauri::command]
pub async fn watch_file(app: AppHandle, path: String) -> Result<(), String> {
    let watcher_state = app.state::<WatcherState>();
    let mut watched = watcher_state.watched_paths.lock().map_err(|e| e.to_string())?;

    if watched.contains(&path) {
        return Ok(());
    }

    // Create watcher if it doesn't exist
    let mut watcher_guard = watcher_state.watcher.lock().map_err(|e| e.to_string())?;
    if watcher_guard.is_none() {
        let app_handle = app.clone();
        let watcher = notify::recommended_watcher(move |res: Result<notify::Event, notify::Error>| {
            match res {
                Ok(event) => {
                    let kind = match event.kind {
                        EventKind::Modify(_) => "modified",
                        EventKind::Remove(_) => "removed",
                        EventKind::Create(_) => "created",
                        _ => return,
                    };

                    for path in event.paths {
                        let _ = app_handle.emit("file:changed", FileChangedEvent {
                            path: path.to_string_lossy().to_string(),
                            kind: kind.to_string(),
                        });
                    }
                }
                Err(_) => {}
            }
        }).map_err(|e| format!("Failed to create watcher: {}", e))?;

        *watcher_guard = Some(watcher);
    }

    // Watch the file's parent directory (notify watches directories)
    let file_path = PathBuf::from(&path);
    if let Some(parent) = file_path.parent() {
        if let Some(ref mut w) = *watcher_guard {
            w.watch(parent, RecursiveMode::NonRecursive)
                .map_err(|e| format!("Failed to watch: {}", e))?;
        }
    }

    watched.insert(path);
    Ok(())
}

#[tauri::command]
pub async fn unwatch_file(app: AppHandle, path: String) -> Result<(), String> {
    let watcher_state = app.state::<WatcherState>();
    let mut watched = watcher_state.watched_paths.lock().map_err(|e| e.to_string())?;
    watched.remove(&path);
    Ok(())
}

#[tauri::command]
pub async fn unwatch_all(app: AppHandle) -> Result<(), String> {
    let watcher_state = app.state::<WatcherState>();
    let mut watched = watcher_state.watched_paths.lock().map_err(|e| e.to_string())?;
    watched.clear();

    let mut watcher_guard = watcher_state.watcher.lock().map_err(|e| e.to_string())?;
    *watcher_guard = None;
    Ok(())
}

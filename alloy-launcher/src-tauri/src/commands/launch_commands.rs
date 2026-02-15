use crate::minecraft::{download, launch, versions};
use crate::state::AppState;
use tauri::{AppHandle, Emitter, State};

#[derive(serde::Serialize)]
pub struct SetupStatus {
    pub is_cached: bool,
    pub version_id: String,
    pub loader_found: bool,
    pub java_found: bool,
    pub java_path: Option<String>,
}

/// Check if everything is ready to launch.
#[tauri::command]
pub async fn check_setup(state: State<'_, AppState>) -> Result<SetupStatus, String> {
    let (version_id, java_path_setting) = {
        let settings = state.settings.lock().unwrap();
        (
            settings.minecraft_version.clone(),
            settings.java_path.clone(),
        )
    };

    let cache_dir = state.cache_dir();
    let is_cached = download::is_version_cached(&cache_dir, &version_id);

    let loader_found = launch::find_loader_jar(&state.base_dir, &cache_dir).is_ok();

    let java_path = java_path_setting.or_else(|| launch::detect_java().ok());
    let java_found = java_path.is_some();

    Ok(SetupStatus {
        is_cached,
        version_id,
        loader_found,
        java_found,
        java_path,
    })
}

/// Launch the game. Downloads if needed, then spawns the Java process.
#[tauri::command]
pub async fn launch_game(
    app: AppHandle,
    state: State<'_, AppState>,
) -> Result<(), String> {
    // Get auth tokens
    let auth = {
        let auth = state.auth.lock().unwrap();
        auth.clone()
            .ok_or("Not authenticated. Please sign in first.")?
    };

    let (version_id, memory_mb, jvm_args, java_path_setting) = {
        let settings = state.settings.lock().unwrap();
        (
            settings.minecraft_version.clone(),
            settings.memory_mb,
            settings.jvm_args.clone(),
            settings.java_path.clone(),
        )
    };

    let cache_dir = state.cache_dir();
    let run_dir = state.run_dir();

    // Ensure run directory exists
    tokio::fs::create_dir_all(&run_dir)
        .await
        .map_err(|e| format!("Failed to create run dir: {}", e))?;

    // Emit preparing state
    let _ = app.emit("launch-state", "preparing");

    // Fetch version details
    let version_list = versions::fetch_version_list(&state.http_client).await?;
    let version_entry = version_list
        .iter()
        .find(|v| v.id == version_id)
        .ok_or(format!("Version {} not found in manifest", version_id))?;

    let version_details =
        versions::fetch_version_details(&state.http_client, version_entry).await?;

    // Download Minecraft if needed
    if !download::is_version_cached(&cache_dir, &version_id) {
        let _ = app.emit("launch-state", "downloading");
        download::setup_version(&app, &state.http_client, &version_details, &cache_dir).await?;
    }

    // Find or download loader JAR
    let loader_jar = match launch::find_loader_jar(&state.base_dir, &cache_dir) {
        Ok(jar) => jar,
        Err(_) => {
            let _ = app.emit("launch-state", "downloading");
            launch::download_loader_jar(&state.http_client, &cache_dir).await?
        }
    };

    // Detect Java
    let java_path = java_path_setting
        .unwrap_or_else(|| launch::detect_java().unwrap_or_else(|_| "java".to_string()));

    // Build and launch
    let _ = app.emit("launch-state", "launching");

    let config = launch::LaunchConfig {
        java_path,
        memory_mb,
        jvm_args,
        mc_token: auth.mc_access_token,
        username: auth.profile.username,
        uuid: auth.profile.uuid,
        version: version_details,
        loader_jar,
        cache_dir,
        run_dir,
    };

    let mut cmd = launch::build_launch_command(&config);

    let child = cmd
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn()
        .map_err(|e| format!("Failed to launch game: {}", e))?;

    let _ = app.emit("launch-state", "running");

    // Monitor game process in background
    let app_handle = app.clone();
    tokio::task::spawn_blocking(move || {
        let output = child.wait_with_output();
        match output {
            Ok(out) => {
                if !out.status.success() {
                    let stderr = String::from_utf8_lossy(&out.stderr);
                    log::error!("Game exited with error: {}", stderr);
                    let _ = app_handle.emit("game-error", stderr.to_string());
                }
            }
            Err(e) => {
                log::error!("Failed to wait for game: {}", e);
                let _ = app_handle.emit("game-error", e.to_string());
            }
        }
        let _ = app_handle.emit("launch-state", "ready");
    });

    Ok(())
}

mod auth;
mod commands;
mod minecraft;
pub mod state;

// TODO: Switch back to Alloy's client ID once app registration is approved
// Alloy: 95ae4c3a-16c9-4a43-9f5c-139ae91fff9a
pub const DEFAULT_CLIENT_ID: &str = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_store::Builder::default().build())
        .manage(state::AppState::new())
        .invoke_handler(tauri::generate_handler![
            commands::auth_commands::login,
            commands::auth_commands::logout,
            commands::auth_commands::check_auth,
            commands::launch_commands::launch_game,
            commands::launch_commands::check_setup,
            commands::settings_commands::get_settings,
            commands::settings_commands::update_settings,
        ])
        .run(tauri::generate_context!())
        .expect("error while running alloy launcher");
}

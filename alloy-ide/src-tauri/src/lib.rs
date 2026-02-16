mod commands;
pub mod mcp;
pub mod state;

use std::sync::Arc;

pub fn run() {
    let app_state = Arc::new(state::AppState::new());
    let mcp_state = app_state.clone();
    let term_state = Arc::new(commands::terminal::TerminalState::new());

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_dialog::init())
        .manage(app_state)
        .manage(term_state)
        .invoke_handler(tauri::generate_handler![
            // Project commands
            commands::project::open_project,
            commands::project::get_recent_projects,
            commands::project::create_project,
            // Asset commands
            commands::assets::analyze_image,
            commands::assets::import_asset,
            // GUI/Animation code generation
            commands::gui::generate_gui_code,
            commands::gui::generate_anim_code,
            // Workspace state persistence
            commands::workspace::save_workspace_state,
            commands::workspace::load_workspace_state,
            // Filesystem commands
            commands::filesystem::list_directory,
            commands::filesystem::read_file,
            commands::filesystem::write_file,
            commands::filesystem::create_file,
            commands::filesystem::create_directory,
            commands::filesystem::delete_path,
            commands::filesystem::rename_path,
            commands::filesystem::search_files,
            commands::filesystem::list_all_files,
            commands::filesystem::git_status,
            commands::filesystem::git_diff,
            commands::filesystem::git_stage,
            commands::filesystem::git_unstage,
            commands::filesystem::git_discard,
            commands::filesystem::git_commit,
            commands::filesystem::replace_in_files,
            commands::filesystem::search_files_advanced,
            commands::filesystem::copy_file_to,
            commands::filesystem::get_file_size,
            // Terminal commands
            commands::terminal::terminal_create,
            commands::terminal::terminal_write,
            commands::terminal::terminal_resize,
            commands::terminal::terminal_destroy,
            // Build commands
            commands::build::run_gradle_task,
            commands::build::list_gradle_tasks,
            // Modpack commands
            commands::modpack::load_modpack_manifest,
            commands::modpack::save_modpack_manifest,
            commands::modpack::add_mod_from_jar,
            commands::modpack::remove_mod_from_pack,
            // AI commands
            commands::ai::ai_send_message,
            commands::ai::ai_get_history,
            commands::ai::ai_clear_history,
            commands::ai::ai_set_config,
            commands::ai::ai_get_config,
            commands::ai::ai_update_editor_state,
            commands::ai::ai_get_pending_actions,
        ])
        .setup(move |_app| {
            // Spawn MCP server on background task (only if ALLOY_MCP env var is set)
            let state = mcp_state;
            tauri::async_runtime::spawn(async move {
                mcp::server::spawn_mcp_server(state).await;
            });
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running alloy ide");
}

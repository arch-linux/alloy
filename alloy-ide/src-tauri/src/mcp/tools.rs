use crate::state::AppState;
use super::types::{ToolDefinition, ToolResult};
use serde_json::{json, Value};
use std::path::Path;

/// Central tool registry. Every IDE action is a tool callable by both
/// the MCP server (external agents) and the Claude API client (built-in AI).
pub struct ToolRegistry;

impl ToolRegistry {
    /// Returns definitions for all available tools.
    pub fn definitions() -> Vec<ToolDefinition> {
        vec![
            // — Project tools —
            ToolDefinition {
                name: "project_open".into(),
                description: "Open a project folder in the IDE".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Absolute path to the project directory" }
                    },
                    "required": ["path"]
                }),
            },
            ToolDefinition {
                name: "project_get_info".into(),
                description: "Get information about the currently open project".into(),
                input_schema: json!({ "type": "object", "properties": {} }),
            },
            ToolDefinition {
                name: "project_list_recent".into(),
                description: "List recently opened projects".into(),
                input_schema: json!({ "type": "object", "properties": {} }),
            },
            // — Filesystem tools —
            ToolDefinition {
                name: "fs_list_directory".into(),
                description: "List files and directories at a path".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Directory path to list" }
                    },
                    "required": ["path"]
                }),
            },
            ToolDefinition {
                name: "fs_read_file".into(),
                description: "Read the contents of a file".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Absolute path to the file" }
                    },
                    "required": ["path"]
                }),
            },
            ToolDefinition {
                name: "fs_write_file".into(),
                description: "Write content to a file (creates or overwrites)".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Absolute path to the file" },
                        "content": { "type": "string", "description": "Content to write" }
                    },
                    "required": ["path", "content"]
                }),
            },
            ToolDefinition {
                name: "fs_create_file".into(),
                description: "Create a new file with optional content".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Absolute path for the new file" },
                        "content": { "type": "string", "description": "Initial content (empty if omitted)" }
                    },
                    "required": ["path"]
                }),
            },
            ToolDefinition {
                name: "fs_delete_file".into(),
                description: "Delete a file".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Absolute path to the file to delete" }
                    },
                    "required": ["path"]
                }),
            },
            ToolDefinition {
                name: "fs_rename".into(),
                description: "Rename or move a file".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "old_path": { "type": "string", "description": "Current file path" },
                        "new_path": { "type": "string", "description": "New file path" }
                    },
                    "required": ["old_path", "new_path"]
                }),
            },
            ToolDefinition {
                name: "fs_search".into(),
                description: "Search file contents with a text query (grep-like)".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "query": { "type": "string", "description": "Search text or pattern" },
                        "path": { "type": "string", "description": "Directory to search in (defaults to project root)" },
                        "glob": { "type": "string", "description": "File glob pattern filter (e.g. \"*.java\")" }
                    },
                    "required": ["query"]
                }),
            },
            // — Editor tools —
            ToolDefinition {
                name: "editor_open_file".into(),
                description: "Open a file in the code editor".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Absolute path to open" }
                    },
                    "required": ["path"]
                }),
            },
            ToolDefinition {
                name: "editor_get_content".into(),
                description: "Get the current content of an open file in the editor".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Path of the open file" }
                    },
                    "required": ["path"]
                }),
            },
            ToolDefinition {
                name: "editor_set_content".into(),
                description: "Replace the entire content of an open file".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Path of the open file" },
                        "content": { "type": "string", "description": "New content" }
                    },
                    "required": ["path", "content"]
                }),
            },
            ToolDefinition {
                name: "editor_insert_at".into(),
                description: "Insert text at a specific line and column in an open file".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Path of the open file" },
                        "line": { "type": "integer", "description": "Line number (1-based)" },
                        "column": { "type": "integer", "description": "Column number (1-based)" },
                        "text": { "type": "string", "description": "Text to insert" }
                    },
                    "required": ["path", "line", "column", "text"]
                }),
            },
            ToolDefinition {
                name: "editor_get_selection".into(),
                description: "Get the currently selected text in the active editor".into(),
                input_schema: json!({ "type": "object", "properties": {} }),
            },
            ToolDefinition {
                name: "editor_list_open".into(),
                description: "List all currently open editor tabs".into(),
                input_schema: json!({ "type": "object", "properties": {} }),
            },
            ToolDefinition {
                name: "editor_close_file".into(),
                description: "Close a file tab in the editor".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "path": { "type": "string", "description": "Path of the file to close" }
                    },
                    "required": ["path"]
                }),
            },
            // — Build tools —
            ToolDefinition {
                name: "build_run".into(),
                description: "Run a Gradle build task".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "task": { "type": "string", "description": "Gradle task name (default: \"build\")" }
                    }
                }),
            },
            ToolDefinition {
                name: "build_get_errors".into(),
                description: "Get current build errors and warnings".into(),
                input_schema: json!({ "type": "object", "properties": {} }),
            },
            // — Terminal tools —
            ToolDefinition {
                name: "terminal_execute".into(),
                description: "Execute a shell command in the integrated terminal".into(),
                input_schema: json!({
                    "type": "object",
                    "properties": {
                        "command": { "type": "string", "description": "Shell command to execute" },
                        "cwd": { "type": "string", "description": "Working directory (defaults to project root)" }
                    },
                    "required": ["command"]
                }),
            },
            ToolDefinition {
                name: "terminal_get_output".into(),
                description: "Get recent terminal output".into(),
                input_schema: json!({ "type": "object", "properties": {} }),
            },
        ]
    }

    /// Execute a tool by name with JSON parameters.
    pub async fn execute(
        name: &str,
        params: Value,
        state: &AppState,
    ) -> ToolResult {
        match name {
            "project_open" => Self::project_open(params, state).await,
            "project_get_info" => Self::project_get_info(state).await,
            "project_list_recent" => Self::project_list_recent(state).await,
            "fs_list_directory" => Self::fs_list_directory(params, state).await,
            "fs_read_file" => Self::fs_read_file(params).await,
            "fs_write_file" => Self::fs_write_file(params).await,
            "fs_create_file" => Self::fs_create_file(params).await,
            "fs_delete_file" => Self::fs_delete_file(params).await,
            "fs_rename" => Self::fs_rename(params).await,
            "fs_search" => Self::fs_search(params, state).await,
            "editor_open_file" => Self::editor_open_file(params, state).await,
            "editor_get_content" => Self::editor_get_content(params).await,
            "editor_set_content" => Self::editor_set_content(params).await,
            "editor_insert_at" => Self::editor_insert_at(params).await,
            "editor_get_selection" => Self::editor_get_selection(state).await,
            "editor_list_open" => Self::editor_list_open(state).await,
            "editor_close_file" => Self::editor_close_file(params, state).await,
            "build_run" => Self::build_run(params, state).await,
            "build_get_errors" => Self::build_get_errors(state).await,
            "terminal_execute" => Self::terminal_execute(params, state).await,
            "terminal_get_output" => Self::terminal_get_output(state).await,
            _ => ToolResult::error(format!("Unknown tool: {}", name)),
        }
    }

    // ── Project tools ──────────────────────────────────────────────

    async fn project_open(params: Value, state: &AppState) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };

        let project_path = Path::new(path);
        if !project_path.exists() || !project_path.is_dir() {
            return ToolResult::error(format!("Not a valid directory: {}", path));
        }

        let name = project_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("Unknown")
            .to_string();

        let mod_json = project_path.join("alloy.mod.json");
        let pack_toml = project_path.join("alloy.pack.toml");

        let (project_type, environment) = if mod_json.exists() {
            let env = std::fs::read_to_string(&mod_json)
                .ok()
                .and_then(|content| {
                    serde_json::from_str::<Value>(&content)
                        .ok()
                        .and_then(|v| v.get("environment").and_then(|e| e.as_str()).map(String::from))
                });
            (crate::state::ProjectType::Mod, env)
        } else if pack_toml.exists() {
            (crate::state::ProjectType::Modpack, None)
        } else {
            (crate::state::ProjectType::Unknown, None)
        };

        let info = crate::state::ProjectInfo {
            name,
            path: path.to_string(),
            project_type,
            environment,
        };

        if let Ok(mut current) = state.current_project.lock() {
            *current = Some(info.clone());
        }

        ToolResult::json(&info)
    }

    async fn project_get_info(state: &AppState) -> ToolResult {
        let current = state.current_project.lock().ok();
        match current.as_deref() {
            Some(Some(info)) => ToolResult::json(info),
            _ => ToolResult::text("No project is currently open"),
        }
    }

    async fn project_list_recent(state: &AppState) -> ToolResult {
        let recents = state.recent_projects.lock().ok();
        match recents.as_deref() {
            Some(list) => ToolResult::json(list),
            None => ToolResult::text("[]"),
        }
    }

    // ── Filesystem tools ───────────────────────────────────────────

    async fn fs_list_directory(params: Value, state: &AppState) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p.to_string(),
            None => {
                // Default to project root
                let current = state.current_project.lock().ok();
                match current.as_deref() {
                    Some(Some(info)) => info.path.clone(),
                    _ => return ToolResult::error("No path specified and no project open"),
                }
            }
        };

        let dir = Path::new(&path);
        if !dir.is_dir() {
            return ToolResult::error(format!("Not a directory: {}", path));
        }

        let read_dir = match std::fs::read_dir(dir) {
            Ok(rd) => rd,
            Err(e) => return ToolResult::error(format!("Cannot read directory: {}", e)),
        };

        let mut entries: Vec<Value> = Vec::new();
        for entry in read_dir.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with('.') {
                continue;
            }
            let metadata = match entry.metadata() {
                Ok(m) => m,
                Err(_) => continue,
            };
            let is_dir = metadata.is_dir();
            entries.push(json!({
                "name": name,
                "path": entry.path().to_string_lossy(),
                "is_dir": is_dir,
            }));
        }

        entries.sort_by(|a, b| {
            let a_dir = a["is_dir"].as_bool().unwrap_or(false);
            let b_dir = b["is_dir"].as_bool().unwrap_or(false);
            if a_dir == b_dir {
                let a_name = a["name"].as_str().unwrap_or("").to_lowercase();
                let b_name = b["name"].as_str().unwrap_or("").to_lowercase();
                a_name.cmp(&b_name)
            } else if a_dir {
                std::cmp::Ordering::Less
            } else {
                std::cmp::Ordering::Greater
            }
        });

        ToolResult::json(&entries)
    }

    async fn fs_read_file(params: Value) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };

        match std::fs::read_to_string(path) {
            Ok(content) => ToolResult::text(content),
            Err(e) => ToolResult::error(format!("Failed to read {}: {}", path, e)),
        }
    }

    async fn fs_write_file(params: Value) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };
        let content = match params.get("content").and_then(|v| v.as_str()) {
            Some(c) => c,
            None => return ToolResult::error("Missing required parameter: content"),
        };

        match std::fs::write(path, content) {
            Ok(()) => ToolResult::text(format!("Written {} bytes to {}", content.len(), path)),
            Err(e) => ToolResult::error(format!("Failed to write {}: {}", path, e)),
        }
    }

    async fn fs_create_file(params: Value) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };

        if Path::new(path).exists() {
            return ToolResult::error(format!("File already exists: {}", path));
        }

        // Create parent directories if needed
        if let Some(parent) = Path::new(path).parent() {
            if !parent.exists() {
                if let Err(e) = std::fs::create_dir_all(parent) {
                    return ToolResult::error(format!("Failed to create directories: {}", e));
                }
            }
        }

        let content = params.get("content").and_then(|v| v.as_str()).unwrap_or("");
        match std::fs::write(path, content) {
            Ok(()) => ToolResult::text(format!("Created {}", path)),
            Err(e) => ToolResult::error(format!("Failed to create {}: {}", path, e)),
        }
    }

    async fn fs_delete_file(params: Value) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };

        match std::fs::remove_file(path) {
            Ok(()) => ToolResult::text(format!("Deleted {}", path)),
            Err(e) => ToolResult::error(format!("Failed to delete {}: {}", path, e)),
        }
    }

    async fn fs_rename(params: Value) -> ToolResult {
        let old_path = match params.get("old_path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: old_path"),
        };
        let new_path = match params.get("new_path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: new_path"),
        };

        match std::fs::rename(old_path, new_path) {
            Ok(()) => ToolResult::text(format!("Renamed {} -> {}", old_path, new_path)),
            Err(e) => ToolResult::error(format!("Failed to rename: {}", e)),
        }
    }

    async fn fs_search(params: Value, state: &AppState) -> ToolResult {
        let query = match params.get("query").and_then(|v| v.as_str()) {
            Some(q) => q.to_string(),
            None => return ToolResult::error("Missing required parameter: query"),
        };

        let search_path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p.to_string(),
            None => {
                let current = state.current_project.lock().ok();
                match current.as_deref() {
                    Some(Some(info)) => info.path.clone(),
                    _ => return ToolResult::error("No search path and no project open"),
                }
            }
        };

        let glob_pattern = params.get("glob").and_then(|v| v.as_str()).map(String::from);
        let query_lower = query.to_lowercase();

        let mut results: Vec<Value> = Vec::new();
        let walker = walkdir::WalkDir::new(&search_path)
            .max_depth(10)
            .into_iter()
            .filter_entry(|e| {
                let name = e.file_name().to_string_lossy();
                !name.starts_with('.')
                    && name != "node_modules"
                    && name != "target"
                    && name != "build"
                    && name != "dist"
            });

        for entry in walker.flatten() {
            if !entry.file_type().is_file() {
                continue;
            }

            let file_path = entry.path();
            let file_name = file_path.file_name().and_then(|n| n.to_str()).unwrap_or("");

            // Apply glob filter
            if let Some(ref glob) = glob_pattern {
                let pattern = glob.trim_start_matches('*');
                if !file_name.ends_with(pattern) {
                    continue;
                }
            }

            // Read and search
            if let Ok(content) = std::fs::read_to_string(file_path) {
                for (i, line) in content.lines().enumerate() {
                    if line.to_lowercase().contains(&query_lower) {
                        results.push(json!({
                            "file": file_path.to_string_lossy(),
                            "line": i + 1,
                            "content": line.trim(),
                        }));
                        if results.len() >= 100 {
                            return ToolResult::json(&json!({
                                "results": results,
                                "truncated": true
                            }));
                        }
                    }
                }
            }
        }

        ToolResult::json(&json!({
            "results": results,
            "truncated": false
        }))
    }

    // ── Editor tools ───────────────────────────────────────────────
    // These tools emit Tauri events so the frontend reacts.
    // For the MCP server (no frontend), they operate on files directly.

    async fn editor_open_file(params: Value, state: &AppState) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };

        if !Path::new(path).exists() {
            return ToolResult::error(format!("File does not exist: {}", path));
        }

        // Store the request in pending_editor_actions for the frontend to pick up
        if let Ok(mut actions) = state.pending_editor_actions.lock() {
            actions.push(json!({ "action": "open", "path": path }));
        }

        ToolResult::text(format!("Opening {} in editor", path))
    }

    async fn editor_get_content(params: Value) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };

        // Read from disk as the source of truth
        match std::fs::read_to_string(path) {
            Ok(content) => ToolResult::text(content),
            Err(e) => ToolResult::error(format!("Failed to read {}: {}", path, e)),
        }
    }

    async fn editor_set_content(params: Value) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };
        let content = match params.get("content").and_then(|v| v.as_str()) {
            Some(c) => c,
            None => return ToolResult::error("Missing required parameter: content"),
        };

        match std::fs::write(path, content) {
            Ok(()) => ToolResult::text(format!("Updated content of {}", path)),
            Err(e) => ToolResult::error(format!("Failed to write {}: {}", path, e)),
        }
    }

    async fn editor_insert_at(params: Value) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };
        let line = match params.get("line").and_then(|v| v.as_u64()) {
            Some(l) => l as usize,
            None => return ToolResult::error("Missing required parameter: line"),
        };
        let column = match params.get("column").and_then(|v| v.as_u64()) {
            Some(c) => c as usize,
            None => return ToolResult::error("Missing required parameter: column"),
        };
        let text = match params.get("text").and_then(|v| v.as_str()) {
            Some(t) => t,
            None => return ToolResult::error("Missing required parameter: text"),
        };

        let content = match std::fs::read_to_string(path) {
            Ok(c) => c,
            Err(e) => return ToolResult::error(format!("Failed to read {}: {}", path, e)),
        };

        let mut lines: Vec<String> = content.lines().map(String::from).collect();

        // Handle trailing newline
        if content.ends_with('\n') {
            lines.push(String::new());
        }

        if line == 0 || line > lines.len() + 1 {
            return ToolResult::error(format!(
                "Line {} out of range (file has {} lines)",
                line,
                lines.len()
            ));
        }

        let line_idx = line - 1;

        if line_idx >= lines.len() {
            // Append at end
            lines.push(text.to_string());
        } else {
            let existing = &lines[line_idx];
            let col_idx = (column.saturating_sub(1)).min(existing.len());
            let mut new_line = String::with_capacity(existing.len() + text.len());
            new_line.push_str(&existing[..col_idx]);
            new_line.push_str(text);
            new_line.push_str(&existing[col_idx..]);
            lines[line_idx] = new_line;
        }

        let result = lines.join("\n");
        match std::fs::write(path, &result) {
            Ok(()) => ToolResult::text(format!(
                "Inserted text at line {}, column {} in {}",
                line, column, path
            )),
            Err(e) => ToolResult::error(format!("Failed to write {}: {}", path, e)),
        }
    }

    async fn editor_get_selection(state: &AppState) -> ToolResult {
        let selection = state.editor_selection.lock().ok();
        match selection.as_deref() {
            Some(Some(sel)) => ToolResult::text(sel.clone()),
            _ => ToolResult::text("No text currently selected"),
        }
    }

    async fn editor_list_open(state: &AppState) -> ToolResult {
        let files = state.open_editor_files.lock().ok();
        match files.as_deref() {
            Some(list) => ToolResult::json(list),
            None => ToolResult::text("[]"),
        }
    }

    async fn editor_close_file(params: Value, state: &AppState) -> ToolResult {
        let path = match params.get("path").and_then(|v| v.as_str()) {
            Some(p) => p,
            None => return ToolResult::error("Missing required parameter: path"),
        };

        if let Ok(mut actions) = state.pending_editor_actions.lock() {
            actions.push(json!({ "action": "close", "path": path }));
        }

        ToolResult::text(format!("Closing {} in editor", path))
    }

    // ── Build tools ────────────────────────────────────────────────

    async fn build_run(params: Value, state: &AppState) -> ToolResult {
        let task = params
            .get("task")
            .and_then(|v| v.as_str())
            .unwrap_or("build");

        let project_path = {
            let current = state.current_project.lock().ok();
            match current.as_deref() {
                Some(Some(info)) => info.path.clone(),
                _ => return ToolResult::error("No project open"),
            }
        };

        let gradlew = if cfg!(windows) {
            "gradlew.bat"
        } else {
            "./gradlew"
        };

        let output = match tokio::process::Command::new(gradlew)
            .arg(task)
            .current_dir(&project_path)
            .output()
            .await
        {
            Ok(o) => o,
            Err(e) => return ToolResult::error(format!("Failed to run gradle: {}", e)),
        };

        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);

        if output.status.success() {
            ToolResult::text(format!("Build succeeded.\n{}", stdout))
        } else {
            ToolResult::error(format!(
                "Build failed (exit {}).\nstdout:\n{}\nstderr:\n{}",
                output.status.code().unwrap_or(-1),
                stdout,
                stderr
            ))
        }
    }

    async fn build_get_errors(state: &AppState) -> ToolResult {
        let errors = state.build_errors.lock().ok();
        match errors.as_deref() {
            Some(list) if !list.is_empty() => ToolResult::json(list),
            _ => ToolResult::text("No build errors"),
        }
    }

    // ── Terminal tools ─────────────────────────────────────────────

    async fn terminal_execute(params: Value, state: &AppState) -> ToolResult {
        let command = match params.get("command").and_then(|v| v.as_str()) {
            Some(c) => c,
            None => return ToolResult::error("Missing required parameter: command"),
        };

        let cwd = match params.get("cwd").and_then(|v| v.as_str()) {
            Some(c) => c.to_string(),
            None => {
                let current = state.current_project.lock().ok();
                match current.as_deref() {
                    Some(Some(info)) => info.path.clone(),
                    _ => ".".to_string(),
                }
            }
        };

        let shell = if cfg!(windows) { "cmd" } else { "sh" };
        let flag = if cfg!(windows) { "/C" } else { "-c" };

        let output = match tokio::process::Command::new(shell)
            .arg(flag)
            .arg(command)
            .current_dir(&cwd)
            .output()
            .await
        {
            Ok(o) => o,
            Err(e) => return ToolResult::error(format!("Failed to execute command: {}", e)),
        };

        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);
        let exit_code = output.status.code().unwrap_or(-1);

        // Store in terminal history
        if let Ok(mut history) = state.terminal_output.lock() {
            history.push(format!("$ {}\n{}{}", command, stdout, stderr));
            // Keep last 50 commands
            if history.len() > 50 {
                let drain_to = history.len() - 50;
                history.drain(..drain_to);
            }
        }

        if output.status.success() {
            ToolResult::text(format!("{}{}", stdout, stderr))
        } else {
            ToolResult::error(format!(
                "Command exited with code {}.\n{}{}",
                exit_code, stdout, stderr
            ))
        }
    }

    async fn terminal_get_output(state: &AppState) -> ToolResult {
        let history = state.terminal_output.lock().ok();
        match history.as_deref() {
            Some(lines) if !lines.is_empty() => {
                ToolResult::text(lines.join("\n---\n"))
            }
            _ => ToolResult::text("No terminal output"),
        }
    }
}

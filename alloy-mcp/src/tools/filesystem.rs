use crate::state::ProjectState;
use crate::types::{ToolDefinition, ToolResult};
use serde_json::{json, Value};
use std::path::Path;

/// Return tool definitions for all filesystem tools.
pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "fs_list_directory".into(),
            description: "List files and directories at a path, sorted with directories first \
                then files, both in alphabetical order. Hidden files (starting with '.') are \
                skipped."
                .into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Absolute path to list. Defaults to the project root if omitted."
                    }
                }
            }),
        },
        ToolDefinition {
            name: "fs_read_file".into(),
            description: "Read the full contents of a file and return it as text.".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Absolute path to the file to read"
                    }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "fs_write_file".into(),
            description: "Write content to a file, creating it if it doesn't exist or \
                overwriting if it does. Parent directories must already exist."
                .into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Absolute path to the file to write"
                    },
                    "content": {
                        "type": "string",
                        "description": "Content to write to the file"
                    }
                },
                "required": ["path", "content"]
            }),
        },
        ToolDefinition {
            name: "fs_create_file".into(),
            description: "Create a new file with optional content. Fails if the file already \
                exists. Creates parent directories automatically."
                .into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Absolute path for the new file"
                    },
                    "content": {
                        "type": "string",
                        "description": "Optional initial content for the file"
                    }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "fs_create_directory".into(),
            description: "Create a directory, including any necessary parent directories.".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Absolute path of the directory to create"
                    }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "fs_delete".into(),
            description: "Delete a file or directory. Directories are removed recursively.".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Absolute path to the file or directory to delete"
                    }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "fs_rename".into(),
            description: "Rename or move a file or directory from old_path to new_path.".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "old_path": {
                        "type": "string",
                        "description": "Current absolute path of the file or directory"
                    },
                    "new_path": {
                        "type": "string",
                        "description": "New absolute path (can be a different directory to move)"
                    }
                },
                "required": ["old_path", "new_path"]
            }),
        },
        ToolDefinition {
            name: "fs_copy".into(),
            description: "Copy a file to a new destination. Does not copy directories.".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "source": {
                        "type": "string",
                        "description": "Absolute path of the source file"
                    },
                    "destination": {
                        "type": "string",
                        "description": "Absolute path for the destination file"
                    }
                },
                "required": ["source", "destination"]
            }),
        },
        ToolDefinition {
            name: "fs_search".into(),
            description: "Search file contents for a text pattern (grep-like). Walks directories \
                up to depth 10, skipping hidden files, node_modules, target, build, and dist \
                directories. Returns matching lines with file path and line number. Limited to \
                100 results."
                .into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "Text pattern to search for (case-sensitive substring match)"
                    },
                    "path": {
                        "type": "string",
                        "description": "Directory to search in. Defaults to the project root."
                    },
                    "glob": {
                        "type": "string",
                        "description": "Optional glob filter for file names (e.g. '*.java', '*.rs')"
                    }
                },
                "required": ["query"]
            }),
        },
        ToolDefinition {
            name: "fs_replace".into(),
            description: "Find and replace text across files in a directory tree. Walks \
                directories with the same filters as fs_search. Supports dry_run mode to preview \
                changes without modifying files."
                .into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "search": {
                        "type": "string",
                        "description": "Text to search for (exact string match)"
                    },
                    "replace": {
                        "type": "string",
                        "description": "Replacement text"
                    },
                    "path": {
                        "type": "string",
                        "description": "Directory to search in. Defaults to the project root."
                    },
                    "glob": {
                        "type": "string",
                        "description": "Optional glob filter for file names (e.g. '*.java', '*.rs')"
                    },
                    "dry_run": {
                        "type": "boolean",
                        "description": "If true, report what would change without modifying files. Default: false."
                    }
                },
                "required": ["search", "replace"]
            }),
        },
    ]
}

/// Dispatch execution to the appropriate filesystem tool handler.
pub async fn execute(name: &str, params: Value, state: &ProjectState) -> ToolResult {
    match name {
        "fs_list_directory" => handle_list_directory(params, state).await,
        "fs_read_file" => handle_read_file(params).await,
        "fs_write_file" => handle_write_file(params).await,
        "fs_create_file" => handle_create_file(params).await,
        "fs_create_directory" => handle_create_directory(params).await,
        "fs_delete" => handle_delete(params).await,
        "fs_rename" => handle_rename(params).await,
        "fs_copy" => handle_copy(params).await,
        "fs_search" => handle_search(params, state).await,
        "fs_replace" => handle_replace(params, state).await,
        _ => ToolResult::error(format!("Unknown filesystem tool: {}", name)),
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/// Resolve a path parameter, falling back to the project root if absent.
fn resolve_path(params: &Value, key: &str, state: &ProjectState) -> Result<String, String> {
    if let Some(p) = params.get(key).and_then(|v| v.as_str()) {
        if p.is_empty() {
            return Err(format!("Parameter '{}' must not be empty", key));
        }
        Ok(p.to_string())
    } else {
        state.project_path()
    }
}

/// Check if a directory entry name should be skipped during walking.
fn is_skipped_dir(name: &str) -> bool {
    name.starts_with('.')
        || name == "node_modules"
        || name == "target"
        || name == "build"
        || name == "dist"
}

/// Check if a filename matches a simple glob pattern.
/// Supports patterns like "*.java", "*.rs", "Cargo.*", etc.
fn matches_glob(filename: &str, pattern: &str) -> bool {
    if pattern == "*" {
        return true;
    }

    if let Some(suffix) = pattern.strip_prefix("*.") {
        // *.ext pattern
        return filename.ends_with(&format!(".{}", suffix));
    }

    if let Some(prefix) = pattern.strip_suffix(".*") {
        // prefix.* pattern
        let base = filename.split('.').next().unwrap_or(filename);
        return base == prefix;
    }

    // Exact match fallback
    filename == pattern
}

// ---------------------------------------------------------------------------
// fs_list_directory
// ---------------------------------------------------------------------------

async fn handle_list_directory(params: Value, state: &ProjectState) -> ToolResult {
    let path = match resolve_path(&params, "path", state) {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };

    let dir = Path::new(&path);
    if !dir.exists() {
        return ToolResult::error(format!("Directory does not exist: {}", path));
    }
    if !dir.is_dir() {
        return ToolResult::error(format!("Path is not a directory: {}", path));
    }

    let entries = match std::fs::read_dir(dir) {
        Ok(rd) => rd,
        Err(e) => return ToolResult::error(format!("Failed to read directory: {}", e)),
    };

    let mut dirs: Vec<String> = Vec::new();
    let mut files: Vec<String> = Vec::new();

    for entry in entries.flatten() {
        let name = entry.file_name().to_string_lossy().to_string();

        // Skip hidden files/directories
        if name.starts_with('.') {
            continue;
        }

        let file_type = match entry.file_type() {
            Ok(ft) => ft,
            Err(_) => continue,
        };

        if file_type.is_dir() {
            dirs.push(format!("{}/", name));
        } else {
            files.push(name);
        }
    }

    dirs.sort_by(|a, b| a.to_lowercase().cmp(&b.to_lowercase()));
    files.sort_by(|a, b| a.to_lowercase().cmp(&b.to_lowercase()));

    let mut listing: Vec<Value> = Vec::new();
    for d in &dirs {
        listing.push(json!({ "name": d, "type": "directory" }));
    }
    for f in &files {
        listing.push(json!({ "name": f, "type": "file" }));
    }

    ToolResult::json(&json!({
        "path": path,
        "entries": listing,
        "total": dirs.len() + files.len()
    }))
}

// ---------------------------------------------------------------------------
// fs_read_file
// ---------------------------------------------------------------------------

async fn handle_read_file(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };

    let file_path = Path::new(path);
    if !file_path.exists() {
        return ToolResult::error(format!("File does not exist: {}", path));
    }
    if !file_path.is_file() {
        return ToolResult::error(format!("Path is not a file: {}", path));
    }

    match std::fs::read_to_string(file_path) {
        Ok(content) => ToolResult::text(content),
        Err(e) => ToolResult::error(format!("Failed to read file: {}", e)),
    }
}

// ---------------------------------------------------------------------------
// fs_write_file
// ---------------------------------------------------------------------------

async fn handle_write_file(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };
    let content = match params.get("content").and_then(|v| v.as_str()) {
        Some(c) => c,
        None => return ToolResult::error("Missing required parameter: content"),
    };

    match std::fs::write(path, content) {
        Ok(_) => {
            let bytes = content.len();
            ToolResult::json(&json!({
                "status": "written",
                "path": path,
                "bytes": bytes
            }))
        }
        Err(e) => ToolResult::error(format!("Failed to write file '{}': {}", path, e)),
    }
}

// ---------------------------------------------------------------------------
// fs_create_file
// ---------------------------------------------------------------------------

async fn handle_create_file(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };
    let content = params
        .get("content")
        .and_then(|v| v.as_str())
        .unwrap_or("");

    let file_path = Path::new(path);

    if file_path.exists() {
        return ToolResult::error(format!(
            "File already exists: {}. Use fs_write_file to overwrite.",
            path
        ));
    }

    // Create parent directories if needed
    if let Some(parent) = file_path.parent() {
        if !parent.exists() {
            if let Err(e) = std::fs::create_dir_all(parent) {
                return ToolResult::error(format!(
                    "Failed to create parent directories for '{}': {}",
                    path, e
                ));
            }
        }
    }

    match std::fs::write(file_path, content) {
        Ok(_) => ToolResult::json(&json!({
            "status": "created",
            "path": path,
            "bytes": content.len()
        })),
        Err(e) => ToolResult::error(format!("Failed to create file '{}': {}", path, e)),
    }
}

// ---------------------------------------------------------------------------
// fs_create_directory
// ---------------------------------------------------------------------------

async fn handle_create_directory(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };

    match std::fs::create_dir_all(path) {
        Ok(_) => ToolResult::json(&json!({
            "status": "created",
            "path": path
        })),
        Err(e) => ToolResult::error(format!("Failed to create directory '{}': {}", path, e)),
    }
}

// ---------------------------------------------------------------------------
// fs_delete
// ---------------------------------------------------------------------------

async fn handle_delete(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };

    let target = Path::new(path);
    if !target.exists() {
        return ToolResult::error(format!("Path does not exist: {}", path));
    }

    let result = if target.is_dir() {
        std::fs::remove_dir_all(target)
    } else {
        std::fs::remove_file(target)
    };

    match result {
        Ok(_) => ToolResult::json(&json!({
            "status": "deleted",
            "path": path
        })),
        Err(e) => ToolResult::error(format!("Failed to delete '{}': {}", path, e)),
    }
}

// ---------------------------------------------------------------------------
// fs_rename
// ---------------------------------------------------------------------------

async fn handle_rename(params: Value) -> ToolResult {
    let old_path = match params.get("old_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: old_path"),
    };
    let new_path = match params.get("new_path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: new_path"),
    };

    if !Path::new(old_path).exists() {
        return ToolResult::error(format!("Source does not exist: {}", old_path));
    }

    match std::fs::rename(old_path, new_path) {
        Ok(_) => ToolResult::json(&json!({
            "status": "renamed",
            "old_path": old_path,
            "new_path": new_path
        })),
        Err(e) => ToolResult::error(format!(
            "Failed to rename '{}' to '{}': {}",
            old_path, new_path, e
        )),
    }
}

// ---------------------------------------------------------------------------
// fs_copy
// ---------------------------------------------------------------------------

async fn handle_copy(params: Value) -> ToolResult {
    let source = match params.get("source").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: source"),
    };
    let destination = match params.get("destination").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: destination"),
    };

    let src = Path::new(source);
    if !src.exists() {
        return ToolResult::error(format!("Source does not exist: {}", source));
    }
    if !src.is_file() {
        return ToolResult::error(format!(
            "Source is not a file: {}. Use fs_copy only for files.",
            source
        ));
    }

    match std::fs::copy(source, destination) {
        Ok(bytes) => ToolResult::json(&json!({
            "status": "copied",
            "source": source,
            "destination": destination,
            "bytes": bytes
        })),
        Err(e) => ToolResult::error(format!(
            "Failed to copy '{}' to '{}': {}",
            source, destination, e
        )),
    }
}

// ---------------------------------------------------------------------------
// fs_search
// ---------------------------------------------------------------------------

async fn handle_search(params: Value, state: &ProjectState) -> ToolResult {
    let query = match params.get("query").and_then(|v| v.as_str()) {
        Some(q) => q,
        None => return ToolResult::error("Missing required parameter: query"),
    };
    let search_path = match resolve_path(&params, "path", state) {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };
    let glob_filter = params.get("glob").and_then(|v| v.as_str());

    let root = Path::new(&search_path);
    if !root.exists() {
        return ToolResult::error(format!("Search path does not exist: {}", search_path));
    }

    let max_results: usize = 100;
    let max_depth: usize = 10;
    let mut results: Vec<Value> = Vec::new();
    let mut truncated = false;

    let walker = walkdir::WalkDir::new(root)
        .max_depth(max_depth)
        .follow_links(false)
        .into_iter();

    for entry in walker.filter_entry(|e| {
        // Skip hidden directories and common noise directories
        let name = e.file_name().to_string_lossy();
        if e.depth() > 0 && is_skipped_dir(&name) {
            return false;
        }
        true
    }) {
        let entry = match entry {
            Ok(e) => e,
            Err(_) => continue,
        };

        if !entry.file_type().is_file() {
            continue;
        }

        let file_name = entry.file_name().to_string_lossy().to_string();

        // Skip hidden files
        if file_name.starts_with('.') {
            continue;
        }

        // Apply glob filter
        if let Some(glob) = glob_filter {
            if !matches_glob(&file_name, glob) {
                continue;
            }
        }

        // Read file and search for the query
        let file_path = entry.path();
        let content = match std::fs::read_to_string(file_path) {
            Ok(c) => c,
            Err(_) => continue, // skip binary or unreadable files
        };

        for (line_num, line) in content.lines().enumerate() {
            if line.contains(query) {
                if results.len() >= max_results {
                    truncated = true;
                    break;
                }
                results.push(json!({
                    "file": file_path.display().to_string(),
                    "line": line_num + 1,
                    "content": line.trim()
                }));
            }
        }

        if truncated {
            break;
        }
    }

    ToolResult::json(&json!({
        "query": query,
        "path": search_path,
        "results": results,
        "count": results.len(),
        "truncated": truncated,
        "max_results": max_results
    }))
}

// ---------------------------------------------------------------------------
// fs_replace
// ---------------------------------------------------------------------------

async fn handle_replace(params: Value, state: &ProjectState) -> ToolResult {
    let search = match params.get("search").and_then(|v| v.as_str()) {
        Some(s) => s,
        None => return ToolResult::error("Missing required parameter: search"),
    };
    let replace = match params.get("replace").and_then(|v| v.as_str()) {
        Some(r) => r,
        None => return ToolResult::error("Missing required parameter: replace"),
    };
    let search_path = match resolve_path(&params, "path", state) {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };
    let glob_filter = params.get("glob").and_then(|v| v.as_str());
    let dry_run = params
        .get("dry_run")
        .and_then(|v| v.as_bool())
        .unwrap_or(false);

    let root = Path::new(&search_path);
    if !root.exists() {
        return ToolResult::error(format!("Search path does not exist: {}", search_path));
    }

    let max_depth: usize = 10;
    let mut modified_files: Vec<Value> = Vec::new();
    let mut total_replacements: usize = 0;

    let walker = walkdir::WalkDir::new(root)
        .max_depth(max_depth)
        .follow_links(false)
        .into_iter();

    for entry in walker.filter_entry(|e| {
        let name = e.file_name().to_string_lossy();
        if e.depth() > 0 && is_skipped_dir(&name) {
            return false;
        }
        true
    }) {
        let entry = match entry {
            Ok(e) => e,
            Err(_) => continue,
        };

        if !entry.file_type().is_file() {
            continue;
        }

        let file_name = entry.file_name().to_string_lossy().to_string();

        // Skip hidden files
        if file_name.starts_with('.') {
            continue;
        }

        // Apply glob filter
        if let Some(glob) = glob_filter {
            if !matches_glob(&file_name, glob) {
                continue;
            }
        }

        let file_path = entry.path();
        let content = match std::fs::read_to_string(file_path) {
            Ok(c) => c,
            Err(_) => continue,
        };

        if !content.contains(search) {
            continue;
        }

        let count = content.matches(search).count();
        total_replacements += count;

        if !dry_run {
            let new_content = content.replace(search, replace);
            if let Err(e) = std::fs::write(file_path, &new_content) {
                return ToolResult::error(format!(
                    "Failed to write replacement to '{}': {}",
                    file_path.display(),
                    e
                ));
            }
        }

        modified_files.push(json!({
            "file": file_path.display().to_string(),
            "replacements": count
        }));
    }

    ToolResult::json(&json!({
        "search": search,
        "replace": replace,
        "path": search_path,
        "dry_run": dry_run,
        "files_modified": modified_files,
        "total_files": modified_files.len(),
        "total_replacements": total_replacements
    }))
}

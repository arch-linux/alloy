use crate::types::{ToolDefinition, ToolResult};
use serde_json::{json, Value};
use std::path::Path;

pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "editor_open".into(),
            description: "Open a file in the editor (records intent for IDE sync)".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Absolute path to the file" }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "editor_list_open".into(),
            description: "List open editor tabs from workspace state".into(),
            input_schema: json!({ "type": "object", "properties": {} }),
        },
        ToolDefinition {
            name: "editor_get_content".into(),
            description: "Get the content of a file".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Absolute path to the file" }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "editor_set_content".into(),
            description: "Replace the entire content of a file".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Absolute path to the file" },
                    "content": { "type": "string", "description": "New file content" }
                },
                "required": ["path", "content"]
            }),
        },
        ToolDefinition {
            name: "editor_insert_at".into(),
            description: "Insert text at a specific line and column in a file".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Absolute path to the file" },
                    "line": { "type": "integer", "description": "Line number (1-based)" },
                    "column": { "type": "integer", "description": "Column number (1-based)" },
                    "text": { "type": "string", "description": "Text to insert" }
                },
                "required": ["path", "line", "column", "text"]
            }),
        },
    ]
}

pub async fn execute(name: &str, params: Value) -> ToolResult {
    match name {
        "editor_open" => editor_open(params).await,
        "editor_list_open" => editor_list_open().await,
        "editor_get_content" => editor_get_content(params).await,
        "editor_set_content" => editor_set_content(params).await,
        "editor_insert_at" => editor_insert_at(params).await,
        _ => ToolResult::error(format!("Unknown editor tool: {}", name)),
    }
}

async fn editor_open(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };

    if !Path::new(path).exists() {
        return ToolResult::error(format!("File does not exist: {}", path));
    }

    // Read the file to confirm it's accessible
    match std::fs::read_to_string(path) {
        Ok(content) => {
            let lines = content.lines().count();
            let size = content.len();
            ToolResult::json(&json!({
                "path": path,
                "lines": lines,
                "size": size,
                "status": "opened"
            }))
        }
        Err(e) => ToolResult::error(format!("Cannot read {}: {}", path, e)),
    }
}

async fn editor_list_open() -> ToolResult {
    // Read workspace state if it exists
    let home = std::env::var("HOME").unwrap_or_default();
    let workspace_path = format!("{}/.alloy-ide/workspace.json", home);

    if Path::new(&workspace_path).exists() {
        match std::fs::read_to_string(&workspace_path) {
            Ok(content) => {
                if let Ok(workspace) = serde_json::from_str::<Value>(&content) {
                    if let Some(tabs) = workspace.get("open_tabs") {
                        return ToolResult::json(tabs);
                    }
                }
                ToolResult::text("[]")
            }
            Err(_) => ToolResult::text("[]"),
        }
    } else {
        ToolResult::text("[]")
    }
}

async fn editor_get_content(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };

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
        Ok(()) => ToolResult::text(format!("Written {} bytes to {}", content.len(), path)),
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

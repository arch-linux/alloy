use crate::state::ProjectState;
use crate::types::{ToolDefinition, ToolResult};
use serde_json::{json, Value};

pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "terminal_execute".into(),
            description: "Execute a shell command and return stdout/stderr".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "command": { "type": "string", "description": "Shell command to execute" },
                    "cwd": { "type": "string", "description": "Working directory (defaults to project root)" },
                    "timeout_secs": { "type": "integer", "description": "Timeout in seconds (default: 60)" }
                },
                "required": ["command"]
            }),
        },
        ToolDefinition {
            name: "terminal_get_output".into(),
            description: "Get recent terminal command history and output".into(),
            input_schema: json!({ "type": "object", "properties": {} }),
        },
    ]
}

pub async fn execute(name: &str, params: Value, state: &ProjectState) -> ToolResult {
    match name {
        "terminal_execute" => terminal_execute(params, state).await,
        "terminal_get_output" => terminal_get_output(state).await,
        _ => ToolResult::error(format!("Unknown terminal tool: {}", name)),
    }
}

async fn terminal_execute(params: Value, state: &ProjectState) -> ToolResult {
    let command = match params.get("command").and_then(|v| v.as_str()) {
        Some(c) => c,
        None => return ToolResult::error("Missing required parameter: command"),
    };

    let cwd = match params.get("cwd").and_then(|v| v.as_str()) {
        Some(c) => c.to_string(),
        None => state.project_path().unwrap_or_else(|_| ".".to_string()),
    };

    let timeout_secs = params
        .get("timeout_secs")
        .and_then(|v| v.as_u64())
        .unwrap_or(60);

    let shell = if cfg!(windows) { "cmd" } else { "sh" };
    let flag = if cfg!(windows) { "/C" } else { "-c" };

    let child = tokio::process::Command::new(shell)
        .arg(flag)
        .arg(command)
        .current_dir(&cwd)
        .output();

    let output = match tokio::time::timeout(
        std::time::Duration::from_secs(timeout_secs),
        child,
    )
    .await
    {
        Ok(Ok(o)) => o,
        Ok(Err(e)) => return ToolResult::error(format!("Failed to execute command: {}", e)),
        Err(_) => return ToolResult::error(format!("Command timed out after {}s", timeout_secs)),
    };

    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    let exit_code = output.status.code().unwrap_or(-1);

    // Store in terminal history
    if let Ok(mut history) = state.terminal_output.lock() {
        history.push(format!("$ {}\n{}{}", command, stdout, stderr));
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

async fn terminal_get_output(state: &ProjectState) -> ToolResult {
    let history = state.terminal_output.lock().ok();
    match history.as_deref() {
        Some(lines) if !lines.is_empty() => ToolResult::text(lines.join("\n---\n")),
        _ => ToolResult::text("No terminal output"),
    }
}

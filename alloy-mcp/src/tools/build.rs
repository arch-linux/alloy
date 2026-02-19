use crate::state::ProjectState;
use crate::types::{ToolDefinition, ToolResult};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};

#[derive(Debug, Clone, Serialize, Deserialize)]
struct BuildError {
    file: String,
    line: usize,
    column: usize,
    message: String,
    severity: String,
}

pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "build_run".into(),
            description: "Run a Gradle build task (build, clean, test, jar, etc.)".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "task": { "type": "string", "description": "Gradle task name (default: \"build\")" },
                    "args": { "type": "array", "items": { "type": "string" }, "description": "Additional Gradle arguments" }
                }
            }),
        },
        ToolDefinition {
            name: "build_list_tasks".into(),
            description: "List available Gradle tasks in the project".into(),
            input_schema: json!({ "type": "object", "properties": {} }),
        },
        ToolDefinition {
            name: "build_validate_env".into(),
            description: "Validate mod environment constraints (client/server import violations)".into(),
            input_schema: json!({ "type": "object", "properties": {} }),
        },
        ToolDefinition {
            name: "build_get_errors".into(),
            description: "Parse build output for Java compiler errors and warnings".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "output": { "type": "string", "description": "Build output to parse (if omitted, returns cached errors)" }
                }
            }),
        },
    ]
}

pub async fn execute(name: &str, params: Value, state: &ProjectState) -> ToolResult {
    match name {
        "build_run" => build_run(params, state).await,
        "build_list_tasks" => build_list_tasks(state).await,
        "build_validate_env" => build_validate_env(state).await,
        "build_get_errors" => build_get_errors(params, state).await,
        _ => ToolResult::error(format!("Unknown build tool: {}", name)),
    }
}

async fn build_run(params: Value, state: &ProjectState) -> ToolResult {
    let project_path = match state.project_path() {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };

    let task = params
        .get("task")
        .and_then(|v| v.as_str())
        .unwrap_or("build");

    let gradlew = if cfg!(windows) {
        "gradlew.bat"
    } else {
        "./gradlew"
    };

    let mut cmd = tokio::process::Command::new(gradlew);
    cmd.arg(task);
    cmd.arg("--console=plain");

    // Add extra args if provided
    if let Some(args) = params.get("args").and_then(|v| v.as_array()) {
        for arg in args {
            if let Some(a) = arg.as_str() {
                cmd.arg(a);
            }
        }
    }

    cmd.current_dir(&project_path);

    let output = match cmd.output().await {
        Ok(o) => o,
        Err(e) => return ToolResult::error(format!("Failed to run gradle: {}", e)),
    };

    let stdout = String::from_utf8_lossy(&output.stdout);
    let stderr = String::from_utf8_lossy(&output.stderr);
    let full_output = format!("{}{}", stdout, stderr);

    // Parse and cache errors
    let errors = parse_build_errors(&full_output);
    if let Ok(mut cached) = state.build_errors.lock() {
        *cached = errors.iter().map(|e| format!("{}:{}: {}", e.file, e.line, e.message)).collect();
    }

    if output.status.success() {
        ToolResult::json(&json!({
            "success": true,
            "task": task,
            "output": full_output.chars().take(10000).collect::<String>(),
            "errors": errors,
        }))
    } else {
        ToolResult::json(&json!({
            "success": false,
            "task": task,
            "exit_code": output.status.code().unwrap_or(-1),
            "output": full_output.chars().take(10000).collect::<String>(),
            "errors": errors,
        }))
    }
}

async fn build_list_tasks(state: &ProjectState) -> ToolResult {
    let project_path = match state.project_path() {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };

    let gradlew = if cfg!(windows) {
        format!("{}\\gradlew.bat", project_path)
    } else {
        format!("{}/gradlew", project_path)
    };

    if std::path::Path::new(&gradlew).exists() {
        // Return common Alloy tasks plus try to list actual tasks
        let output = tokio::process::Command::new("sh")
            .arg("-c")
            .arg(format!("cd '{}' && ./gradlew tasks --all --console=plain 2>/dev/null | grep -E '^[a-zA-Z]' | head -50", project_path))
            .output()
            .await;

        match output {
            Ok(o) if o.status.success() => {
                let output_str = String::from_utf8_lossy(&o.stdout).to_string();
                let tasks: Vec<&str> = output_str
                    .lines()
                    .filter(|l| l.contains(" - "))
                    .map(|l| l.split(" - ").next().unwrap_or(l).trim())
                    .collect();
                if !tasks.is_empty() {
                    return ToolResult::json(&tasks);
                }
            }
            _ => {}
        }

        // Fallback: return common tasks
        ToolResult::json(&vec![
            "build", "clean", "setupWorkspace", "launchClient",
            "launchServer", "jar", "test",
        ])
    } else {
        ToolResult::error("No Gradle wrapper found in project")
    }
}

async fn build_validate_env(state: &ProjectState) -> ToolResult {
    let project_path = match state.project_path() {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };

    let mod_json_path = std::path::Path::new(&project_path).join("alloy.mod.json");
    if !mod_json_path.exists() {
        return ToolResult::text("No alloy.mod.json found — skipping environment validation");
    }

    let mod_json = match std::fs::read_to_string(&mod_json_path) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(format!("Failed to read alloy.mod.json: {}", e)),
    };

    let parsed: Value = match serde_json::from_str(&mod_json) {
        Ok(v) => v,
        Err(e) => return ToolResult::error(format!("Failed to parse alloy.mod.json: {}", e)),
    };

    let environment = parsed
        .get("environment")
        .and_then(|e| e.as_str())
        .unwrap_or("both");

    if environment == "both" {
        return ToolResult::json(&json!({
            "environment": "both",
            "violations": [],
            "message": "No restrictions for universal mods"
        }));
    }

    let forbidden_patterns: Vec<&str> = if environment == "server" {
        vec![
            "net.alloymc.api.client", "net.alloymc.api.render",
            "net.alloymc.api.gui", "net.alloymc.api.hud",
            "net.alloymc.api.particle", "net.alloymc.api.texture",
            "net.alloymc.api.model", "net.alloymc.api.screen",
            "AlloyRenderer", "AlloyScreen", "AlloyHud",
            "GuiComponent", "ParticleEmitter",
        ]
    } else {
        vec![
            "net.alloymc.api.server", "net.alloymc.api.command.server",
            "net.alloymc.api.world.server", "ServerCommandSource",
            "DedicatedServer",
        ]
    };

    let mut violations: Vec<Value> = Vec::new();
    let src_dir = std::path::Path::new(&project_path).join("src");
    if !src_dir.exists() {
        return ToolResult::json(&json!({
            "environment": environment,
            "violations": [],
            "message": "No src directory found"
        }));
    }

    for entry in walkdir::WalkDir::new(&src_dir)
        .into_iter()
        .filter_map(|e| e.ok())
    {
        let path = entry.path();
        if !path.is_file() || path.extension().and_then(|e| e.to_str()) != Some("java") {
            continue;
        }

        let content = match std::fs::read_to_string(path) {
            Ok(c) => c,
            Err(_) => continue,
        };

        let file_path = path.to_string_lossy().to_string();

        for (line_num, line) in content.lines().enumerate() {
            let trimmed = line.trim();

            if trimmed.starts_with("import ") {
                for pattern in &forbidden_patterns {
                    if trimmed.contains(pattern) {
                        let env_label = if environment == "server" { "client-only" } else { "server-only" };
                        violations.push(json!({
                            "file": file_path,
                            "line": line_num + 1,
                            "message": format!("Importing {} API \"{}\" in a {}-only mod", env_label, pattern, environment),
                            "severity": "warning"
                        }));
                        break;
                    }
                }
            }

            if !trimmed.starts_with("import ") && !trimmed.starts_with("//") && !trimmed.starts_with("*") {
                for pattern in &forbidden_patterns {
                    if !pattern.contains('.') && trimmed.contains(pattern) {
                        let env_label = if environment == "server" { "client-only" } else { "server-only" };
                        violations.push(json!({
                            "file": file_path,
                            "line": line_num + 1,
                            "message": format!("Reference to {} class \"{}\" in a {}-only mod", env_label, pattern, environment),
                            "severity": "warning"
                        }));
                        break;
                    }
                }
            }
        }
    }

    ToolResult::json(&json!({
        "environment": environment,
        "violations": violations,
        "count": violations.len()
    }))
}

async fn build_get_errors(params: Value, state: &ProjectState) -> ToolResult {
    if let Some(output) = params.get("output").and_then(|v| v.as_str()) {
        let errors = parse_build_errors(output);
        return ToolResult::json(&errors);
    }

    let cached = state.build_errors.lock().ok();
    match cached.as_deref() {
        Some(list) if !list.is_empty() => ToolResult::json(list),
        _ => ToolResult::text("No build errors"),
    }
}

fn parse_build_errors(output: &str) -> Vec<BuildError> {
    let mut errors = Vec::new();

    for line in output.lines() {
        if let Some(err) = parse_java_error(line) {
            errors.push(err);
        }
    }

    errors
}

fn parse_java_error(line: &str) -> Option<BuildError> {
    let trimmed = line.trim();
    let java_idx = trimmed.find(".java:")?;
    let after_java = &trimmed[java_idx + 6..];

    let colon_idx = after_java.find(':')?;
    let line_str = &after_java[..colon_idx];
    let line_num: usize = line_str.parse().ok()?;

    let rest = after_java[colon_idx + 1..].trim();

    let (severity, message) = if rest.starts_with("error:") {
        ("error".to_string(), rest[6..].trim().to_string())
    } else if rest.starts_with("warning:") {
        ("warning".to_string(), rest[8..].trim().to_string())
    } else {
        return None;
    };

    let file = trimmed[..java_idx + 5].to_string();

    Some(BuildError {
        file,
        line: line_num,
        column: 0,
        message,
        severity,
    })
}

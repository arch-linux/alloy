use serde::Serialize;
use std::io::BufRead;
use std::process::{Command, Stdio};
use tauri::{AppHandle, Emitter};

#[derive(Debug, Clone, Serialize)]
pub struct BuildError {
    pub file: String,
    pub line: usize,
    pub column: usize,
    pub message: String,
    pub severity: String, // "error" | "warning"
}

#[derive(Debug, Clone, Serialize)]
pub struct BuildResult {
    pub success: bool,
    pub errors: Vec<BuildError>,
    pub output: String,
}

/// Determine the Gradle wrapper path for the platform
fn gradle_cmd(project_path: &str) -> (String, Vec<String>) {
    let wrapper = if cfg!(target_os = "windows") {
        format!("{}\\gradlew.bat", project_path)
    } else {
        format!("{}/gradlew", project_path)
    };

    let wrapper_path = std::path::Path::new(&wrapper);
    if wrapper_path.exists() {
        if cfg!(target_os = "windows") {
            (wrapper, vec![])
        } else {
            ("sh".to_string(), vec![wrapper])
        }
    } else {
        // Fall back to system gradle
        ("gradle".to_string(), vec![])
    }
}

/// Parse Java compiler errors from Gradle output
fn parse_build_errors(output: &str) -> Vec<BuildError> {
    let mut errors = Vec::new();

    for line in output.lines() {
        // Java compiler error format: /path/File.java:42: error: message
        if let Some(captures) = parse_java_error(line) {
            errors.push(captures);
        }
        // Gradle failure format
        if line.contains("FAILURE:") || line.contains("BUILD FAILED") {
            // Already captured via exit code
        }
    }

    errors
}

fn parse_java_error(line: &str) -> Option<BuildError> {
    // Pattern: /path/to/File.java:LINE: error: message
    // Pattern: /path/to/File.java:LINE: warning: message
    let trimmed = line.trim();

    // Look for .java: pattern
    let java_idx = trimmed.find(".java:")?;
    let after_java = &trimmed[java_idx + 6..]; // skip ".java:"

    // Parse line number
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

    let file = trimmed[..java_idx + 5].to_string(); // Include ".java"

    Some(BuildError {
        file,
        line: line_num,
        column: 0,
        message,
        severity,
    })
}

#[tauri::command]
pub async fn run_gradle_task(
    project_path: String,
    task: String,
    app: AppHandle,
) -> Result<BuildResult, String> {
    let (cmd, mut base_args) = gradle_cmd(&project_path);
    base_args.push(task.clone());
    base_args.push("--console=plain".to_string());

    let mut child = Command::new(&cmd)
        .args(&base_args)
        .current_dir(&project_path)
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .map_err(|e| format!("Failed to run gradle: {}", e))?;

    let stdout = child.stdout.take().ok_or("No stdout")?;
    let stderr = child.stderr.take().ok_or("No stderr")?;

    let app_out = app.clone();
    let stdout_handle = std::thread::spawn(move || {
        let reader = std::io::BufReader::new(stdout);
        let mut output = String::new();
        for line in reader.lines().flatten() {
            let _ = app_out.emit("build:output", &line);
            output.push_str(&line);
            output.push('\n');
        }
        output
    });

    let app_err = app.clone();
    let stderr_handle = std::thread::spawn(move || {
        let reader = std::io::BufReader::new(stderr);
        let mut output = String::new();
        for line in reader.lines().flatten() {
            let _ = app_err.emit("build:output", &line);
            output.push_str(&line);
            output.push('\n');
        }
        output
    });

    let status = child.wait().map_err(|e| e.to_string())?;
    let stdout_output = stdout_handle.join().map_err(|_| "Thread error")?;
    let stderr_output = stderr_handle.join().map_err(|_| "Thread error")?;

    let full_output = format!("{}{}", stdout_output, stderr_output);
    let errors = parse_build_errors(&full_output);
    let success = status.success();

    let result = BuildResult {
        success,
        errors: errors.clone(),
        output: full_output,
    };

    let _ = app.emit("build:done", &result);

    Ok(result)
}

/// Validate mod environment constraints by scanning source files
#[tauri::command]
pub async fn validate_environment(project_path: String) -> Result<Vec<BuildError>, String> {
    // Read alloy.mod.json to determine environment
    let mod_json_path = std::path::Path::new(&project_path).join("alloy.mod.json");
    if !mod_json_path.exists() {
        return Ok(vec![]);
    }

    let mod_json = std::fs::read_to_string(&mod_json_path)
        .map_err(|e| format!("Failed to read alloy.mod.json: {}", e))?;

    let parsed: serde_json::Value = serde_json::from_str(&mod_json)
        .map_err(|e| format!("Failed to parse alloy.mod.json: {}", e))?;

    let environment = parsed
        .get("environment")
        .and_then(|e| e.as_str())
        .unwrap_or("both");

    if environment == "both" {
        return Ok(vec![]); // No restrictions for universal mods
    }

    // Define forbidden imports based on environment
    let forbidden_patterns: Vec<&str> = if environment == "server" {
        vec![
            "net.alloymc.api.client",
            "net.alloymc.api.render",
            "net.alloymc.api.gui",
            "net.alloymc.api.hud",
            "net.alloymc.api.particle",
            "net.alloymc.api.texture",
            "net.alloymc.api.model",
            "net.alloymc.api.screen",
            "AlloyRenderer",
            "AlloyScreen",
            "AlloyHud",
            "GuiComponent",
            "ParticleEmitter",
        ]
    } else {
        // client environment
        vec![
            "net.alloymc.api.server",
            "net.alloymc.api.command.server",
            "net.alloymc.api.world.server",
            "ServerCommandSource",
            "DedicatedServer",
        ]
    };

    let mut violations = Vec::new();

    // Scan Java source files
    let src_dir = std::path::Path::new(&project_path).join("src");
    if !src_dir.exists() {
        return Ok(vec![]);
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

            // Check import statements
            if trimmed.starts_with("import ") {
                for pattern in &forbidden_patterns {
                    if trimmed.contains(pattern) {
                        let env_label = if environment == "server" {
                            "client-only"
                        } else {
                            "server-only"
                        };
                        violations.push(BuildError {
                            file: file_path.clone(),
                            line: line_num + 1,
                            column: 0,
                            message: format!(
                                "Importing {} API \"{}\" in a {}-only mod",
                                env_label, pattern, environment
                            ),
                            severity: "warning".to_string(),
                        });
                        break;
                    }
                }
            }

            // Check direct class references (not in import lines)
            if !trimmed.starts_with("import ") && !trimmed.starts_with("//") && !trimmed.starts_with("*") {
                for pattern in &forbidden_patterns {
                    // Only check short class names (not package paths which are caught by import check)
                    if !pattern.contains('.') && trimmed.contains(pattern) {
                        let env_label = if environment == "server" {
                            "client-only"
                        } else {
                            "server-only"
                        };
                        violations.push(BuildError {
                            file: file_path.clone(),
                            line: line_num + 1,
                            column: 0,
                            message: format!(
                                "Reference to {} class \"{}\" in a {}-only mod",
                                env_label, pattern, environment
                            ),
                            severity: "warning".to_string(),
                        });
                        break;
                    }
                }
            }
        }
    }

    // Scan .block.json files for block-specific validation
    for entry in walkdir::WalkDir::new(&project_path)
        .max_depth(3)
        .into_iter()
        .filter_map(|e| e.ok())
    {
        let path = entry.path();
        if !path.is_file() {
            continue;
        }
        let name = path.file_name().and_then(|n| n.to_str()).unwrap_or("");
        if !name.ends_with(".block.json") {
            continue;
        }

        let block_content = match std::fs::read_to_string(path) {
            Ok(c) => c,
            Err(_) => continue,
        };
        let block_json: serde_json::Value = match serde_json::from_str(&block_content) {
            Ok(v) => v,
            Err(_) => continue,
        };

        let block_name = block_json.get("name").and_then(|n| n.as_str()).unwrap_or("unknown");
        let file_path = path.to_string_lossy().to_string();

        // Check for missing textures
        let texture_mode = block_json.get("texture_mode").and_then(|m| m.as_str()).unwrap_or("all");
        if let Some(textures) = block_json.get("textures") {
            if texture_mode == "all" {
                if textures.get("all").and_then(|v| v.as_str()).is_none() {
                    violations.push(BuildError {
                        file: file_path.clone(),
                        line: 0,
                        column: 0,
                        message: format!("Block \"{}\" has no texture assigned", block_name),
                        severity: "warning".to_string(),
                    });
                }
            } else {
                for face in &["top", "bottom", "north", "south", "east", "west"] {
                    if textures.get(*face).and_then(|v| v.as_str()).is_none() {
                        violations.push(BuildError {
                            file: file_path.clone(),
                            line: 0,
                            column: 0,
                            message: format!("Block \"{}\" missing texture for {} face", block_name, face),
                            severity: "warning".to_string(),
                        });
                    }
                }
            }
        }

        // Check GUI file exists if referenced
        let has_gui = block_json.get("has_gui").and_then(|v| v.as_bool()).unwrap_or(false);
        if has_gui {
            if let Some(gui_file) = block_json.get("gui_file").and_then(|v| v.as_str()) {
                let gui_path = path.parent().unwrap_or(std::path::Path::new(".")).join(gui_file);
                if !gui_path.exists() {
                    violations.push(BuildError {
                        file: file_path.clone(),
                        line: 0,
                        column: 0,
                        message: format!("Block \"{}\" references GUI file \"{}\" which does not exist", block_name, gui_file),
                        severity: "error".to_string(),
                    });
                }
            }

            // Server-only mod with GUI block
            if environment == "server" {
                violations.push(BuildError {
                    file: file_path.clone(),
                    line: 0,
                    column: 0,
                    message: format!("Block \"{}\" has GUI enabled but mod is server-only", block_name),
                    severity: "error".to_string(),
                });
            }
        }
    }

    Ok(violations)
}

#[tauri::command]
pub async fn list_gradle_tasks(project_path: String) -> Result<Vec<String>, String> {
    // Return common Alloy mod tasks
    let wrapper = if cfg!(target_os = "windows") {
        format!("{}\\gradlew.bat", project_path)
    } else {
        format!("{}/gradlew", project_path)
    };

    let has_wrapper = std::path::Path::new(&wrapper).exists();

    if has_wrapper {
        Ok(vec![
            "build".to_string(),
            "clean".to_string(),
            "setupWorkspace".to_string(),
            "launchClient".to_string(),
            "launchServer".to_string(),
            "jar".to_string(),
            "test".to_string(),
        ])
    } else {
        Err("No Gradle wrapper found in project".to_string())
    }
}

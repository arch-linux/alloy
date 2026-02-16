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

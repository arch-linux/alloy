use crate::state::ProjectState;
use crate::types::{ToolDefinition, ToolResult};
use serde_json::{json, Value};

/// Return all git tool definitions.
pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "git_init".into(),
            description: "Initialize a new git repository".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Path to initialize the repo in (defaults to project root)"
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_status".into(),
            description: "Get the current git status including branch, staged, unstaged, and untracked files".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "cwd": {
                        "type": "string",
                        "description": "Working directory override"
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_diff".into(),
            description: "Show the diff of changes in the working tree or staging area".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "Specific file to diff"
                    },
                    "staged": {
                        "type": "boolean",
                        "description": "If true, show staged changes (--cached)"
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_stage".into(),
            description: "Stage files for commit (git add)".into(),
            input_schema: json!({
                "type": "object",
                "required": ["paths"],
                "properties": {
                    "paths": {
                        "oneOf": [
                            { "type": "string" },
                            { "type": "array", "items": { "type": "string" } }
                        ],
                        "description": "File path(s) to stage. Use \".\" to stage all."
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_unstage".into(),
            description: "Unstage files from the staging area (git restore --staged)".into(),
            input_schema: json!({
                "type": "object",
                "required": ["paths"],
                "properties": {
                    "paths": {
                        "oneOf": [
                            { "type": "string" },
                            { "type": "array", "items": { "type": "string" } }
                        ],
                        "description": "File path(s) to unstage"
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_commit".into(),
            description: "Create a git commit with the given message".into(),
            input_schema: json!({
                "type": "object",
                "required": ["message"],
                "properties": {
                    "message": {
                        "type": "string",
                        "description": "Commit message"
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_push".into(),
            description: "Push commits to a remote repository".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "remote": {
                        "type": "string",
                        "description": "Remote name (default: origin)"
                    },
                    "branch": {
                        "type": "string",
                        "description": "Branch to push (default: current branch)"
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_pull".into(),
            description: "Pull changes from a remote repository".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "remote": {
                        "type": "string",
                        "description": "Remote name (default: origin)"
                    },
                    "branch": {
                        "type": "string",
                        "description": "Branch to pull from"
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_discard".into(),
            description: "Discard unstaged changes to a file (git checkout -- <path>)".into(),
            input_schema: json!({
                "type": "object",
                "required": ["path"],
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "File path to discard changes for"
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_show".into(),
            description: "Show the contents of a file at HEAD".into(),
            input_schema: json!({
                "type": "object",
                "required": ["path"],
                "properties": {
                    "path": {
                        "type": "string",
                        "description": "File path to show at HEAD"
                    }
                }
            }),
        },
        ToolDefinition {
            name: "git_log".into(),
            description: "Show recent commit history".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "count": {
                        "type": "integer",
                        "description": "Number of commits to show (default: 10)"
                    }
                }
            }),
        },
    ]
}

/// Execute a git tool by name.
pub async fn execute(name: &str, params: Value, state: &ProjectState) -> ToolResult {
    match name {
        "git_init" => git_init(params, state).await,
        "git_status" => git_status(params, state).await,
        "git_diff" => git_diff(params, state).await,
        "git_stage" => git_stage(params, state).await,
        "git_unstage" => git_unstage(params, state).await,
        "git_commit" => git_commit(params, state).await,
        "git_push" => git_push(params, state).await,
        "git_pull" => git_pull(params, state).await,
        "git_discard" => git_discard(params, state).await,
        "git_show" => git_show(params, state).await,
        "git_log" => git_log(params, state).await,
        _ => ToolResult::error(format!("Unknown git tool: {}", name)),
    }
}

// ---------------------------------------------------------------------------
// Helper
// ---------------------------------------------------------------------------

async fn run_git(args: &[&str], cwd: &str) -> Result<String, String> {
    let output = tokio::process::Command::new("git")
        .args(args)
        .current_dir(cwd)
        .output()
        .await
        .map_err(|e| format!("Failed to run git: {}", e))?;

    let stdout = String::from_utf8_lossy(&output.stdout).to_string();
    let stderr = String::from_utf8_lossy(&output.stderr).to_string();

    if output.status.success() {
        Ok(stdout)
    } else {
        Err(format!("{}{}", stdout, stderr))
    }
}

/// Resolve the working directory from params "cwd" or fall back to the project path.
fn resolve_cwd(params: &Value, state: &ProjectState) -> Result<String, String> {
    if let Some(cwd) = params.get("cwd").and_then(|v| v.as_str()) {
        if !cwd.is_empty() {
            return Ok(cwd.to_string());
        }
    }
    state.project_path()
}

/// Extract `paths` parameter as a Vec<String>, accepting both a single string and an array.
fn extract_paths(params: &Value) -> Result<Vec<String>, String> {
    match params.get("paths") {
        Some(Value::String(s)) => Ok(vec![s.clone()]),
        Some(Value::Array(arr)) => {
            let mut paths = Vec::with_capacity(arr.len());
            for item in arr {
                match item.as_str() {
                    Some(s) => paths.push(s.to_string()),
                    None => return Err("Each path must be a string".to_string()),
                }
            }
            if paths.is_empty() {
                return Err("paths array must not be empty".to_string());
            }
            Ok(paths)
        }
        Some(_) => Err("paths must be a string or array of strings".to_string()),
        None => Err("Missing required parameter: paths".to_string()),
    }
}

// ---------------------------------------------------------------------------
// Tool implementations
// ---------------------------------------------------------------------------

async fn git_init(params: Value, state: &ProjectState) -> ToolResult {
    let base = match state.project_path() {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };

    let path = params
        .get("path")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .unwrap_or(base);

    match run_git(&["init"], &path).await {
        Ok(output) => ToolResult::text(output.trim_end()),
        Err(e) => ToolResult::error(e),
    }
}

async fn git_status(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let output = match run_git(&["status", "--porcelain", "-b"], &cwd).await {
        Ok(o) => o,
        Err(e) => return ToolResult::error(e),
    };

    let mut branch = String::new();
    let mut ahead: u64 = 0;
    let mut behind: u64 = 0;
    let mut staged: Vec<Value> = Vec::new();
    let mut unstaged: Vec<Value> = Vec::new();
    let mut untracked: Vec<Value> = Vec::new();

    for line in output.lines() {
        if let Some(rest) = line.strip_prefix("## ") {
            // Parse branch line: "main...origin/main [ahead 2, behind 1]"
            // or simply: "main" / "No commits yet on main"
            let (branch_part, tracking_info) = match rest.find(" [") {
                Some(idx) => (&rest[..idx], Some(&rest[idx + 2..rest.len() - 1])),
                None => (rest, None),
            };

            // Branch name is everything before "..." (tracking remote) or the whole string.
            branch = match branch_part.find("...") {
                Some(idx) => branch_part[..idx].to_string(),
                None => branch_part.to_string(),
            };

            // Parse ahead/behind from tracking info like "ahead 2, behind 1" or "ahead 3"
            if let Some(info) = tracking_info {
                for segment in info.split(", ") {
                    let segment = segment.trim();
                    if let Some(n) = segment.strip_prefix("ahead ") {
                        ahead = n.trim().parse().unwrap_or(0);
                    } else if let Some(n) = segment.strip_prefix("behind ") {
                        behind = n.trim().parse().unwrap_or(0);
                    }
                }
            }

            continue;
        }

        // Porcelain format: XY filename
        // X = index status, Y = work-tree status
        if line.len() < 3 {
            continue;
        }

        let x = line.as_bytes()[0] as char;
        let y = line.as_bytes()[1] as char;
        let file = &line[3..];

        // Untracked files
        if x == '?' && y == '?' {
            untracked.push(json!(file));
            continue;
        }

        // Staged changes (index column)
        match x {
            'M' => staged.push(json!({ "status": "modified", "file": file })),
            'A' => staged.push(json!({ "status": "added", "file": file })),
            'D' => staged.push(json!({ "status": "deleted", "file": file })),
            'R' => staged.push(json!({ "status": "renamed", "file": file })),
            _ => {}
        }

        // Unstaged changes (work-tree column)
        match y {
            'M' => unstaged.push(json!({ "status": "modified", "file": file })),
            'D' => unstaged.push(json!({ "status": "deleted", "file": file })),
            _ => {}
        }
    }

    let result = json!({
        "branch": branch,
        "ahead": ahead,
        "behind": behind,
        "staged": staged,
        "unstaged": unstaged,
        "untracked": untracked,
    });

    ToolResult::json(&result)
}

async fn git_diff(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let staged = params
        .get("staged")
        .and_then(|v| v.as_bool())
        .unwrap_or(false);

    let path = params.get("path").and_then(|v| v.as_str());

    let mut args: Vec<&str> = vec!["diff"];
    if staged {
        args.push("--cached");
    }
    if let Some(p) = path {
        args.push("--");
        args.push(p);
    }

    match run_git(&args, &cwd).await {
        Ok(output) => {
            if output.is_empty() {
                ToolResult::text("No differences found.")
            } else {
                ToolResult::text(output)
            }
        }
        Err(e) => ToolResult::error(e),
    }
}

async fn git_stage(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let paths = match extract_paths(&params) {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };

    let mut args: Vec<&str> = vec!["add"];
    for p in &paths {
        args.push(p.as_str());
    }

    match run_git(&args, &cwd).await {
        Ok(_) => {
            let label = if paths.len() == 1 && paths[0] == "." {
                "all files".to_string()
            } else {
                paths.join(", ")
            };
            ToolResult::text(format!("Staged: {}", label))
        }
        Err(e) => ToolResult::error(e),
    }
}

async fn git_unstage(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let paths = match extract_paths(&params) {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };

    let mut args: Vec<&str> = vec!["restore", "--staged"];
    for p in &paths {
        args.push(p.as_str());
    }

    match run_git(&args, &cwd).await {
        Ok(_) => {
            ToolResult::text(format!("Unstaged: {}", paths.join(", ")))
        }
        Err(e) => ToolResult::error(e),
    }
}

async fn git_commit(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let message = match params.get("message").and_then(|v| v.as_str()) {
        Some(m) if !m.is_empty() => m,
        _ => return ToolResult::error("Missing required parameter: message"),
    };

    match run_git(&["commit", "-m", message], &cwd).await {
        Ok(output) => ToolResult::text(output.trim_end()),
        Err(e) => ToolResult::error(e),
    }
}

async fn git_push(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let remote = params
        .get("remote")
        .and_then(|v| v.as_str())
        .unwrap_or("origin");

    let branch = params.get("branch").and_then(|v| v.as_str());

    let mut args: Vec<&str> = vec!["push", remote];
    if let Some(b) = branch {
        args.push(b);
    }

    match run_git(&args, &cwd).await {
        Ok(output) => {
            // git push often writes to stderr even on success; combine both.
            if output.is_empty() {
                ToolResult::text(format!("Pushed to {}", remote))
            } else {
                ToolResult::text(output.trim_end())
            }
        }
        Err(e) => ToolResult::error(e),
    }
}

async fn git_pull(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let remote = params
        .get("remote")
        .and_then(|v| v.as_str())
        .unwrap_or("origin");

    let branch = params.get("branch").and_then(|v| v.as_str());

    let mut args: Vec<&str> = vec!["pull", remote];
    if let Some(b) = branch {
        args.push(b);
    }

    match run_git(&args, &cwd).await {
        Ok(output) => ToolResult::text(output.trim_end()),
        Err(e) => ToolResult::error(e),
    }
}

async fn git_discard(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) if !p.is_empty() => p,
        _ => return ToolResult::error("Missing required parameter: path"),
    };

    match run_git(&["checkout", "--", path], &cwd).await {
        Ok(_) => ToolResult::text(format!("Discarded changes to: {}", path)),
        Err(e) => ToolResult::error(e),
    }
}

async fn git_show(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) if !p.is_empty() => p,
        _ => return ToolResult::error("Missing required parameter: path"),
    };

    let ref_path = format!("HEAD:{}", path);
    match run_git(&["show", &ref_path], &cwd).await {
        Ok(output) => ToolResult::text(output),
        Err(e) => ToolResult::error(e),
    }
}

async fn git_log(params: Value, state: &ProjectState) -> ToolResult {
    let cwd = match resolve_cwd(&params, state) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(e),
    };

    let count = params
        .get("count")
        .and_then(|v| v.as_u64())
        .unwrap_or(10);

    let count_str = count.to_string();
    match run_git(&["log", "--oneline", "-n", &count_str], &cwd).await {
        Ok(output) => {
            if output.is_empty() {
                ToolResult::text("No commits found.")
            } else {
                ToolResult::text(output.trim_end())
            }
        }
        Err(e) => ToolResult::error(e),
    }
}

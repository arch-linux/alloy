use serde::Serialize;
use std::path::{Path, PathBuf};

#[derive(Debug, Serialize)]
pub struct FileEntry {
    pub name: String,
    pub path: String,
    pub is_dir: bool,
    pub extension: Option<String>,
}

#[tauri::command]
pub async fn list_directory(path: String) -> Result<Vec<FileEntry>, String> {
    let dir = Path::new(&path);
    if !dir.is_dir() {
        return Err(format!("Not a directory: {}", path));
    }

    let mut entries: Vec<FileEntry> = Vec::new();

    let read_dir = std::fs::read_dir(dir).map_err(|e| e.to_string())?;

    for entry in read_dir {
        let entry = entry.map_err(|e| e.to_string())?;
        let file_name = entry.file_name().to_string_lossy().to_string();

        // Skip hidden files and common noise
        if file_name.starts_with('.') {
            continue;
        }
        if file_name == "node_modules" || file_name == "target" || file_name == "build" || file_name == "dist" {
            continue;
        }

        let metadata = entry.metadata().map_err(|e| e.to_string())?;
        let is_dir = metadata.is_dir();
        let extension = if is_dir {
            None
        } else {
            entry
                .path()
                .extension()
                .and_then(|e| e.to_str())
                .map(String::from)
        };

        entries.push(FileEntry {
            name: file_name,
            path: entry.path().to_string_lossy().to_string(),
            is_dir,
            extension,
        });
    }

    // Sort: directories first, then alphabetical
    entries.sort_by(|a, b| {
        if a.is_dir == b.is_dir {
            a.name.to_lowercase().cmp(&b.name.to_lowercase())
        } else if a.is_dir {
            std::cmp::Ordering::Less
        } else {
            std::cmp::Ordering::Greater
        }
    });

    Ok(entries)
}

#[tauri::command]
pub async fn read_file(path: String) -> Result<String, String> {
    std::fs::read_to_string(&path).map_err(|e| format!("Failed to read {}: {}", path, e))
}

#[tauri::command]
pub async fn write_file(path: String, content: String) -> Result<(), String> {
    std::fs::write(&path, &content).map_err(|e| format!("Failed to write {}: {}", path, e))
}

#[tauri::command]
pub async fn create_file(path: String, content: Option<String>) -> Result<(), String> {
    let file_path = Path::new(&path);
    if file_path.exists() {
        return Err(format!("File already exists: {}", path));
    }
    if let Some(parent) = file_path.parent() {
        std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
    }
    std::fs::write(&path, content.unwrap_or_default())
        .map_err(|e| format!("Failed to create {}: {}", path, e))
}

#[tauri::command]
pub async fn create_directory(path: String) -> Result<(), String> {
    std::fs::create_dir_all(&path).map_err(|e| format!("Failed to create directory {}: {}", path, e))
}

#[tauri::command]
pub async fn delete_path(path: String) -> Result<(), String> {
    let p = Path::new(&path);
    if p.is_dir() {
        std::fs::remove_dir_all(&path).map_err(|e| e.to_string())
    } else {
        std::fs::remove_file(&path).map_err(|e| e.to_string())
    }
}

#[tauri::command]
pub async fn rename_path(old_path: String, new_path: String) -> Result<(), String> {
    std::fs::rename(&old_path, &new_path)
        .map_err(|e| format!("Failed to rename: {}", e))
}

#[derive(Debug, Serialize)]
pub struct SearchResult {
    pub path: String,
    pub name: String,
    pub line_number: usize,
    pub line_content: String,
}

#[tauri::command]
pub async fn search_files(query: String, search_path: String) -> Result<Vec<SearchResult>, String> {
    use std::io::BufRead;
    let mut results = Vec::new();
    let query_lower = query.to_lowercase();

    fn walk_and_search(
        dir: &Path,
        query: &str,
        results: &mut Vec<SearchResult>,
    ) -> Result<(), String> {
        let read_dir = match std::fs::read_dir(dir) {
            Ok(rd) => rd,
            Err(_) => return Ok(()),
        };

        for entry in read_dir.flatten() {
            let path = entry.path();
            let name = entry.file_name().to_string_lossy().to_string();

            // Skip hidden, node_modules, target, build, dist, .git
            if name.starts_with('.') || name == "node_modules" || name == "target" || name == "build" || name == "dist" {
                continue;
            }

            if path.is_dir() {
                walk_and_search(&path, query, results)?;
            } else {
                // Only search text files by extension
                let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("");
                let searchable = matches!(
                    ext,
                    "java" | "json" | "toml" | "xml" | "yml" | "yaml" | "properties"
                    | "md" | "txt" | "gradle" | "kts" | "cfg" | "conf" | "ts" | "tsx"
                    | "js" | "jsx" | "css" | "html" | "rs" | "py" | "sh" | "bat"
                );
                if !searchable { continue; }

                if let Ok(file) = std::fs::File::open(&path) {
                    let reader = std::io::BufReader::new(file);
                    for (i, line) in reader.lines().enumerate() {
                        if let Ok(line) = line {
                            if line.to_lowercase().contains(query) {
                                results.push(SearchResult {
                                    path: path.to_string_lossy().to_string(),
                                    name: name.clone(),
                                    line_number: i + 1,
                                    line_content: line.chars().take(200).collect(),
                                });
                                if results.len() >= 500 { return Ok(()); }
                            }
                        }
                    }
                }
            }
        }
        Ok(())
    }

    walk_and_search(Path::new(&search_path), &query_lower, &mut results)?;
    Ok(results)
}

#[derive(Debug, Serialize)]
pub struct ReplaceResult {
    pub files_changed: usize,
    pub replacements: usize,
}

#[tauri::command]
pub async fn replace_in_files(
    query: String,
    replacement: String,
    search_path: String,
    case_sensitive: bool,
    regex_mode: bool,
) -> Result<ReplaceResult, String> {
    let mut files_changed = 0usize;
    let mut replacements = 0usize;

    let compiled_regex = if regex_mode {
        match regex::RegexBuilder::new(&query)
            .case_insensitive(!case_sensitive)
            .build()
        {
            Ok(r) => Some(r),
            Err(e) => return Err(format!("Invalid regex: {}", e)),
        }
    } else {
        None
    };

    fn walk_replace(
        dir: &Path,
        query: &str,
        replacement: &str,
        case_sensitive: bool,
        compiled_regex: &Option<regex::Regex>,
        files_changed: &mut usize,
        replacements: &mut usize,
    ) -> Result<(), String> {
        let read_dir = match std::fs::read_dir(dir) {
            Ok(rd) => rd,
            Err(_) => return Ok(()),
        };

        for entry in read_dir.flatten() {
            let path = entry.path();
            let name = entry.file_name().to_string_lossy().to_string();

            if name.starts_with('.')
                || name == "node_modules"
                || name == "target"
                || name == "build"
                || name == "dist"
            {
                continue;
            }

            if path.is_dir() {
                walk_replace(
                    &path,
                    query,
                    replacement,
                    case_sensitive,
                    compiled_regex,
                    files_changed,
                    replacements,
                )?;
            } else {
                let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("");
                let searchable = matches!(
                    ext,
                    "java" | "json" | "toml" | "xml" | "yml" | "yaml" | "properties"
                    | "md" | "txt" | "gradle" | "kts" | "cfg" | "conf" | "ts" | "tsx"
                    | "js" | "jsx" | "css" | "html" | "rs" | "py" | "sh" | "bat"
                );
                if !searchable {
                    continue;
                }

                if let Ok(content) = std::fs::read_to_string(&path) {
                    let new_content = if let Some(re) = compiled_regex {
                        let result = re.replace_all(&content, replacement).to_string();
                        if result == content {
                            continue;
                        }
                        let count = re.find_iter(&content).count();
                        *replacements += count;
                        result
                    } else if case_sensitive {
                        let count = content.matches(query).count();
                        if count == 0 {
                            continue;
                        }
                        *replacements += count;
                        content.replace(query, replacement)
                    } else {
                        let lower_content = content.to_lowercase();
                        let lower_query = query.to_lowercase();
                        let count = lower_content.matches(&lower_query).count();
                        if count == 0 {
                            continue;
                        }
                        // Case-insensitive replace
                        *replacements += count;
                        let mut result = String::with_capacity(content.len());
                        let mut last = 0;
                        let lc = content.to_lowercase();
                        let lq = query.to_lowercase();
                        while let Some(pos) = lc[last..].find(&lq) {
                            result.push_str(&content[last..last + pos]);
                            result.push_str(replacement);
                            last += pos + query.len();
                        }
                        result.push_str(&content[last..]);
                        result
                    };

                    if let Err(e) = std::fs::write(&path, &new_content) {
                        return Err(format!("Failed to write {}: {}", path.display(), e));
                    }
                    *files_changed += 1;
                }
            }
        }
        Ok(())
    }

    walk_replace(
        Path::new(&search_path),
        &query,
        &replacement,
        case_sensitive,
        &compiled_regex,
        &mut files_changed,
        &mut replacements,
    )?;

    Ok(ReplaceResult {
        files_changed,
        replacements,
    })
}

#[tauri::command]
pub async fn search_files_advanced(
    query: String,
    search_path: String,
    case_sensitive: bool,
    regex_mode: bool,
) -> Result<Vec<SearchResult>, String> {
    use std::io::BufRead;
    let mut results = Vec::new();

    let compiled_regex = if regex_mode {
        match regex::RegexBuilder::new(&query)
            .case_insensitive(!case_sensitive)
            .build()
        {
            Ok(r) => Some(r),
            Err(e) => return Err(format!("Invalid regex: {}", e)),
        }
    } else {
        None
    };

    fn walk_search(
        dir: &Path,
        query: &str,
        case_sensitive: bool,
        compiled_regex: &Option<regex::Regex>,
        results: &mut Vec<SearchResult>,
    ) -> Result<(), String> {
        let read_dir = match std::fs::read_dir(dir) {
            Ok(rd) => rd,
            Err(_) => return Ok(()),
        };

        for entry in read_dir.flatten() {
            let path = entry.path();
            let name = entry.file_name().to_string_lossy().to_string();

            if name.starts_with('.')
                || name == "node_modules"
                || name == "target"
                || name == "build"
                || name == "dist"
            {
                continue;
            }

            if path.is_dir() {
                walk_search(&path, query, case_sensitive, compiled_regex, results)?;
            } else {
                let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("");
                let searchable = matches!(
                    ext,
                    "java" | "json" | "toml" | "xml" | "yml" | "yaml" | "properties"
                    | "md" | "txt" | "gradle" | "kts" | "cfg" | "conf" | "ts" | "tsx"
                    | "js" | "jsx" | "css" | "html" | "rs" | "py" | "sh" | "bat"
                );
                if !searchable {
                    continue;
                }

                if let Ok(file) = std::fs::File::open(&path) {
                    let reader = std::io::BufReader::new(file);
                    for (i, line) in reader.lines().enumerate() {
                        if let Ok(line) = line {
                            let matches = if let Some(re) = compiled_regex {
                                re.is_match(&line)
                            } else if case_sensitive {
                                line.contains(query)
                            } else {
                                line.to_lowercase().contains(&query.to_lowercase())
                            };

                            if matches {
                                results.push(SearchResult {
                                    path: path.to_string_lossy().to_string(),
                                    name: name.clone(),
                                    line_number: i + 1,
                                    line_content: line.chars().take(200).collect(),
                                });
                                if results.len() >= 500 {
                                    return Ok(());
                                }
                            }
                        }
                    }
                }
            }
        }
        Ok(())
    }

    walk_search(
        Path::new(&search_path),
        &query,
        case_sensitive,
        &compiled_regex,
        &mut results,
    )?;

    Ok(results)
}

#[derive(Debug, Serialize)]
pub struct QuickOpenEntry {
    pub name: String,
    pub path: String,
    pub relative_path: String,
}

#[tauri::command]
pub async fn list_all_files(root_path: String) -> Result<Vec<QuickOpenEntry>, String> {
    let root = PathBuf::from(&root_path);
    let mut entries = Vec::new();

    fn walk(dir: &Path, root: &Path, entries: &mut Vec<QuickOpenEntry>) {
        let read_dir = match std::fs::read_dir(dir) {
            Ok(rd) => rd,
            Err(_) => return,
        };
        for entry in read_dir.flatten() {
            let name = entry.file_name().to_string_lossy().to_string();
            if name.starts_with('.')
                || name == "node_modules"
                || name == "target"
                || name == "build"
                || name == "dist"
                || name == ".gradle"
            {
                continue;
            }
            let path = entry.path();
            if path.is_dir() {
                walk(&path, root, entries);
            } else {
                let relative = path
                    .strip_prefix(root)
                    .unwrap_or(&path)
                    .to_string_lossy()
                    .to_string();
                entries.push(QuickOpenEntry {
                    name,
                    path: path.to_string_lossy().to_string(),
                    relative_path: relative,
                });
                if entries.len() >= 5000 {
                    return;
                }
            }
        }
    }

    walk(&root, &root, &mut entries);
    entries.sort_by(|a, b| a.name.to_lowercase().cmp(&b.name.to_lowercase()));
    Ok(entries)
}

#[tauri::command]
pub async fn copy_file_to(source: String, dest_dir: String) -> Result<String, String> {
    let src_path = Path::new(&source);
    let file_name = src_path
        .file_name()
        .ok_or("Invalid source file name")?
        .to_string_lossy()
        .to_string();
    let dest_path = PathBuf::from(&dest_dir).join(&file_name);

    if dest_path.exists() {
        return Err(format!("File already exists: {}", dest_path.display()));
    }

    std::fs::copy(&source, &dest_path)
        .map_err(|e| format!("Failed to copy: {}", e))?;

    Ok(dest_path.to_string_lossy().to_string())
}

#[tauri::command]
pub async fn get_file_size(path: String) -> Result<u64, String> {
    let metadata = std::fs::metadata(&path).map_err(|e| e.to_string())?;
    Ok(metadata.len())
}

#[derive(Debug, Serialize)]
pub struct GitStatus {
    pub branch: String,
    pub files: Vec<GitFileStatus>,
    pub staged: Vec<GitFileStatus>,
    pub ahead: u32,
    pub behind: u32,
}

#[derive(Debug, Serialize)]
pub struct GitFileStatus {
    pub path: String,
    pub status: String,
}

#[tauri::command]
pub async fn git_status(project_path: String) -> Result<GitStatus, String> {
    // Get branch name
    let branch_output = std::process::Command::new("git")
        .args(["rev-parse", "--abbrev-ref", "HEAD"])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git not found: {}", e))?;

    let branch = String::from_utf8_lossy(&branch_output.stdout)
        .trim()
        .to_string();

    if branch.is_empty() {
        return Err("Not a git repository".to_string());
    }

    // Get ahead/behind counts
    let mut ahead: u32 = 0;
    let mut behind: u32 = 0;
    if let Ok(ab_output) = std::process::Command::new("git")
        .args(["rev-list", "--left-right", "--count", &format!("HEAD...@{{u}}")])
        .current_dir(&project_path)
        .output()
    {
        let ab_text = String::from_utf8_lossy(&ab_output.stdout);
        let parts: Vec<&str> = ab_text.trim().split('\t').collect();
        if parts.len() == 2 {
            ahead = parts[0].parse().unwrap_or(0);
            behind = parts[1].parse().unwrap_or(0);
        }
    }

    // Get file statuses with porcelain v1 (XY format)
    let status_output = std::process::Command::new("git")
        .args(["status", "--porcelain=v1"])
        .current_dir(&project_path)
        .output()
        .map_err(|e| e.to_string())?;

    let status_text = String::from_utf8_lossy(&status_output.stdout);
    let mut files: Vec<GitFileStatus> = Vec::new();
    let mut staged: Vec<GitFileStatus> = Vec::new();

    for line in status_text.lines().filter(|l| !l.is_empty()) {
        let x = line.chars().next().unwrap_or(' ');
        let y = line.chars().nth(1).unwrap_or(' ');
        let file_path = line.get(3..).unwrap_or("").to_string();

        // Index (staged) status - first char
        if x != ' ' && x != '?' {
            let status = match x {
                'M' => "modified",
                'A' => "added",
                'D' => "deleted",
                'R' => "renamed",
                'C' => "copied",
                _ => "changed",
            };
            staged.push(GitFileStatus {
                path: file_path.clone(),
                status: status.to_string(),
            });
        }

        // Working tree (unstaged) status - second char
        if y != ' ' {
            let status = match y {
                'M' => "modified",
                'D' => "deleted",
                '?' => "untracked",
                _ => "changed",
            };
            files.push(GitFileStatus {
                path: file_path,
                status: status.to_string(),
            });
        } else if x == '?' {
            // ?? = untracked
            files.push(GitFileStatus {
                path: file_path,
                status: "untracked".to_string(),
            });
        }
    }

    Ok(GitStatus { branch, files, staged, ahead, behind })
}

#[tauri::command]
pub async fn git_show_file(project_path: String, file_path: String) -> Result<String, String> {
    let output = std::process::Command::new("git")
        .args(["show", &format!("HEAD:{}", file_path)])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git show failed: {}", e))?;

    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).to_string());
    }
    Ok(String::from_utf8_lossy(&output.stdout).to_string())
}

#[tauri::command]
pub async fn git_push(project_path: String) -> Result<String, String> {
    let output = std::process::Command::new("git")
        .args(["push"])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git push failed: {}", e))?;

    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).to_string());
    }
    Ok(String::from_utf8_lossy(&output.stdout).to_string()
        + &String::from_utf8_lossy(&output.stderr))
}

#[tauri::command]
pub async fn git_pull(project_path: String) -> Result<String, String> {
    let output = std::process::Command::new("git")
        .args(["pull"])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git pull failed: {}", e))?;

    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).to_string());
    }
    Ok(String::from_utf8_lossy(&output.stdout).to_string())
}

#[tauri::command]
pub async fn git_diff(project_path: String, file_path: Option<String>) -> Result<String, String> {
    let mut args = vec!["diff".to_string()];
    if let Some(fp) = &file_path {
        args.push("--".to_string());
        args.push(fp.clone());
    }

    let output = std::process::Command::new("git")
        .args(&args)
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git diff failed: {}", e))?;

    let diff_text = String::from_utf8_lossy(&output.stdout).to_string();

    // If empty, try staged diff
    if diff_text.is_empty() {
        let mut staged_args = vec!["diff".to_string(), "--cached".to_string()];
        if let Some(fp) = &file_path {
            staged_args.push("--".to_string());
            staged_args.push(fp.clone());
        }
        let staged_output = std::process::Command::new("git")
            .args(&staged_args)
            .current_dir(&project_path)
            .output()
            .map_err(|e| format!("git diff --cached failed: {}", e))?;
        return Ok(String::from_utf8_lossy(&staged_output.stdout).to_string());
    }

    Ok(diff_text)
}

#[tauri::command]
pub async fn git_stage(project_path: String, file_path: String) -> Result<(), String> {
    let output = std::process::Command::new("git")
        .args(["add", &file_path])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git add failed: {}", e))?;

    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).to_string());
    }
    Ok(())
}

#[tauri::command]
pub async fn git_unstage(project_path: String, file_path: String) -> Result<(), String> {
    let output = std::process::Command::new("git")
        .args(["restore", "--staged", &file_path])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git restore failed: {}", e))?;

    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).to_string());
    }
    Ok(())
}

#[tauri::command]
pub async fn git_discard(project_path: String, file_path: String) -> Result<(), String> {
    let output = std::process::Command::new("git")
        .args(["checkout", "--", &file_path])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git checkout failed: {}", e))?;

    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).to_string());
    }
    Ok(())
}

#[tauri::command]
pub async fn git_commit(project_path: String, message: String) -> Result<(), String> {
    let output = std::process::Command::new("git")
        .args(["commit", "-m", &message])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git commit failed: {}", e))?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr).to_string();
        return Err(stderr);
    }
    Ok(())
}

#[tauri::command]
pub async fn git_init(project_path: String) -> Result<String, String> {
    let output = std::process::Command::new("git")
        .args(["init"])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git init failed: {}", e))?;

    if !output.status.success() {
        let stderr = String::from_utf8_lossy(&output.stderr).to_string();
        return Err(stderr);
    }
    Ok(String::from_utf8_lossy(&output.stdout).trim().to_string())
}

#[derive(serde::Serialize)]
pub struct BlameLine {
    pub hash: String,
    pub author: String,
    pub date: String,
    pub summary: String,
}

#[tauri::command]
pub async fn git_blame_file(
    project_path: String,
    file_path: String,
) -> Result<Vec<BlameLine>, String> {
    let output = std::process::Command::new("git")
        .args(["blame", "--porcelain", &file_path])
        .current_dir(&project_path)
        .output()
        .map_err(|e| format!("git blame failed: {}", e))?;

    if !output.status.success() {
        return Err(String::from_utf8_lossy(&output.stderr).to_string());
    }

    let stdout = String::from_utf8_lossy(&output.stdout);
    let mut result: Vec<BlameLine> = Vec::new();

    let mut current_hash = String::new();
    let mut current_author = String::new();
    let mut current_date = String::new();
    let mut current_summary = String::new();

    for line in stdout.lines() {
        if line.starts_with('\t') {
            // This is the actual source line — finalize this blame entry
            result.push(BlameLine {
                hash: current_hash.clone(),
                author: current_author.clone(),
                date: current_date.clone(),
                summary: current_summary.clone(),
            });
        } else if let Some(rest) = line.strip_prefix("author ") {
            current_author = rest.to_string();
        } else if let Some(rest) = line.strip_prefix("author-time ") {
            // Convert Unix timestamp to ISO date
            if let Ok(ts) = rest.parse::<i64>() {
                let secs = ts;
                // Simple UTC date formatting
                let datetime = chrono_lite_format(secs);
                current_date = datetime;
            }
        } else if let Some(rest) = line.strip_prefix("summary ") {
            current_summary = rest.to_string();
        } else if line.len() >= 40 && line.chars().take(40).all(|c| c.is_ascii_hexdigit()) {
            // This is a commit hash line (40 hex chars followed by line numbers)
            current_hash = line[..40].to_string();
        }
    }

    Ok(result)
}

/// Simple Unix timestamp to "YYYY-MM-DD" formatting without chrono dependency.
fn chrono_lite_format(timestamp: i64) -> String {
    // Calculate date from Unix timestamp (UTC)
    let secs_per_day: i64 = 86400;
    let days = timestamp / secs_per_day;

    // Days since 1970-01-01
    let mut y = 1970i64;
    let mut remaining = days;

    loop {
        let days_in_year = if is_leap(y) { 366 } else { 365 };
        if remaining < days_in_year {
            break;
        }
        remaining -= days_in_year;
        y += 1;
    }

    let month_days = if is_leap(y) {
        [31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    } else {
        [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    };

    let mut m = 0usize;
    for (i, &md) in month_days.iter().enumerate() {
        if remaining < md {
            m = i;
            break;
        }
        remaining -= md;
    }

    let d = remaining + 1;
    format!("{:04}-{:02}-{:02}", y, m + 1, d)
}

fn is_leap(y: i64) -> bool {
    (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
}

use crate::state::ProjectState;
use crate::types::{ToolDefinition, ToolResult};
use serde_json::{json, Value};

pub fn definitions() -> Vec<ToolDefinition> {
    vec![
        ToolDefinition {
            name: "code_symbols".into(),
            description: "List classes, methods, and fields in a Java file (regex-based)".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "path": { "type": "string", "description": "Path to the Java file" }
                },
                "required": ["path"]
            }),
        },
        ToolDefinition {
            name: "code_references".into(),
            description: "Find all references to a symbol across the project (grep-based)".into(),
            input_schema: json!({
                "type": "object",
                "properties": {
                    "symbol": { "type": "string", "description": "Symbol name to search for" },
                    "path": { "type": "string", "description": "Directory to search in (defaults to project src/)" }
                },
                "required": ["symbol"]
            }),
        },
        ToolDefinition {
            name: "code_diagnostics".into(),
            description: "Get environment violations and basic Java checks for the project".into(),
            input_schema: json!({ "type": "object", "properties": {} }),
        },
    ]
}

pub async fn execute(name: &str, params: Value, state: &ProjectState) -> ToolResult {
    match name {
        "code_symbols" => code_symbols(params).await,
        "code_references" => code_references(params, state).await,
        "code_diagnostics" => code_diagnostics(state).await,
        _ => ToolResult::error(format!("Unknown code tool: {}", name)),
    }
}

async fn code_symbols(params: Value) -> ToolResult {
    let path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p,
        None => return ToolResult::error("Missing required parameter: path"),
    };

    let content = match std::fs::read_to_string(path) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(format!("Failed to read {}: {}", path, e)),
    };

    let mut classes: Vec<Value> = Vec::new();
    let mut methods: Vec<Value> = Vec::new();
    let mut fields: Vec<Value> = Vec::new();
    let mut imports: Vec<String> = Vec::new();
    let mut package = String::new();

    let class_re = regex::Regex::new(
        r"(?:public|private|protected)?\s*(?:static\s+)?(?:final\s+)?(?:abstract\s+)?(?:class|interface|enum|record)\s+(\w+)"
    ).unwrap();

    let method_re = regex::Regex::new(
        r"(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?(?:synchronized\s+)?(?:abstract\s+)?(\w+(?:<[^>]+>)?)\s+(\w+)\s*\("
    ).unwrap();

    let field_re = regex::Regex::new(
        r"(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?(\w+(?:<[^>]+>)?)\s+(\w+)\s*[;=]"
    ).unwrap();

    for (line_num, line) in content.lines().enumerate() {
        let trimmed = line.trim();

        // Package
        if trimmed.starts_with("package ") {
            package = trimmed
                .strip_prefix("package ")
                .and_then(|s| s.strip_suffix(';'))
                .unwrap_or("")
                .trim()
                .to_string();
            continue;
        }

        // Imports
        if trimmed.starts_with("import ") {
            let imp = trimmed
                .strip_prefix("import ")
                .and_then(|s| s.strip_suffix(';'))
                .unwrap_or("")
                .trim()
                .to_string();
            imports.push(imp);
            continue;
        }

        // Skip comments
        if trimmed.starts_with("//") || trimmed.starts_with("/*") || trimmed.starts_with("*") {
            continue;
        }

        // Classes
        if let Some(caps) = class_re.captures(trimmed) {
            if let Some(name) = caps.get(1) {
                classes.push(json!({
                    "name": name.as_str(),
                    "line": line_num + 1,
                }));
            }
        }

        // Methods (only if not a class declaration line)
        if !trimmed.contains("class ") && !trimmed.contains("interface ") {
            if let Some(caps) = method_re.captures(trimmed) {
                if let (Some(return_type), Some(name)) = (caps.get(1), caps.get(2)) {
                    let name_str = name.as_str();
                    // Skip constructors (return type == class name) handled naturally
                    if name_str != "if" && name_str != "for" && name_str != "while" && name_str != "switch" {
                        methods.push(json!({
                            "name": name_str,
                            "return_type": return_type.as_str(),
                            "line": line_num + 1,
                        }));
                    }
                }
            }
        }

        // Fields (only outside method bodies — heuristic: no parentheses in line)
        if !trimmed.contains('(') && !trimmed.contains(')') {
            if let Some(caps) = field_re.captures(trimmed) {
                if let (Some(field_type), Some(name)) = (caps.get(1), caps.get(2)) {
                    fields.push(json!({
                        "name": name.as_str(),
                        "type": field_type.as_str(),
                        "line": line_num + 1,
                    }));
                }
            }
        }
    }

    ToolResult::json(&json!({
        "file": path,
        "package": package,
        "imports": imports,
        "classes": classes,
        "methods": methods,
        "fields": fields,
    }))
}

async fn code_references(params: Value, state: &ProjectState) -> ToolResult {
    let symbol = match params.get("symbol").and_then(|v| v.as_str()) {
        Some(s) => s.to_string(),
        None => return ToolResult::error("Missing required parameter: symbol"),
    };

    let search_path = match params.get("path").and_then(|v| v.as_str()) {
        Some(p) => p.to_string(),
        None => {
            match state.project_path() {
                Ok(p) => format!("{}/src", p),
                Err(_) => return ToolResult::error("No search path and no project open"),
            }
        }
    };

    let mut results: Vec<Value> = Vec::new();

    for entry in walkdir::WalkDir::new(&search_path)
        .max_depth(15)
        .into_iter()
        .filter_entry(|e| {
            let name = e.file_name().to_string_lossy();
            !name.starts_with('.') && name != "node_modules" && name != "target" && name != "build"
        })
        .flatten()
    {
        if !entry.file_type().is_file() {
            continue;
        }

        let path = entry.path();
        let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("");
        if ext != "java" && ext != "kt" && ext != "gradle" && ext != "kts" {
            continue;
        }

        if let Ok(content) = std::fs::read_to_string(path) {
            for (i, line) in content.lines().enumerate() {
                if line.contains(&symbol) {
                    results.push(json!({
                        "file": path.to_string_lossy(),
                        "line": i + 1,
                        "content": line.trim(),
                    }));
                    if results.len() >= 100 {
                        return ToolResult::json(&json!({
                            "symbol": symbol,
                            "results": results,
                            "truncated": true
                        }));
                    }
                }
            }
        }
    }

    ToolResult::json(&json!({
        "symbol": symbol,
        "results": results,
        "count": results.len(),
        "truncated": false
    }))
}

async fn code_diagnostics(state: &ProjectState) -> ToolResult {
    let project_path = match state.project_path() {
        Ok(p) => p,
        Err(e) => return ToolResult::error(e),
    };

    let mod_json_path = std::path::Path::new(&project_path).join("alloy.mod.json");
    if !mod_json_path.exists() {
        return ToolResult::json(&json!({
            "diagnostics": [],
            "message": "No alloy.mod.json found"
        }));
    }

    let mod_json = match std::fs::read_to_string(&mod_json_path) {
        Ok(c) => c,
        Err(e) => return ToolResult::error(format!("Failed to read alloy.mod.json: {}", e)),
    };

    let parsed: Value = match serde_json::from_str(&mod_json) {
        Ok(v) => v,
        Err(e) => return ToolResult::error(format!("Failed to parse: {}", e)),
    };

    let environment = parsed.get("environment").and_then(|e| e.as_str()).unwrap_or("both");

    let mut diagnostics: Vec<Value> = Vec::new();

    if environment != "both" {
        let forbidden: Vec<&str> = if environment == "server" {
            vec!["net.alloymc.api.client", "net.alloymc.api.render", "net.alloymc.api.gui",
                 "AlloyRenderer", "AlloyScreen", "AlloyHud", "GuiComponent"]
        } else {
            vec!["net.alloymc.api.server", "ServerCommandSource", "DedicatedServer"]
        };

        let src_dir = std::path::Path::new(&project_path).join("src");
        if src_dir.exists() {
            for entry in walkdir::WalkDir::new(&src_dir).into_iter().flatten() {
                let path = entry.path();
                if !path.is_file() || path.extension().and_then(|e| e.to_str()) != Some("java") {
                    continue;
                }
                if let Ok(content) = std::fs::read_to_string(path) {
                    for (line_num, line) in content.lines().enumerate() {
                        let trimmed = line.trim();
                        if trimmed.starts_with("import ") {
                            for pattern in &forbidden {
                                if trimmed.contains(pattern) {
                                    diagnostics.push(json!({
                                        "file": path.to_string_lossy(),
                                        "line": line_num + 1,
                                        "severity": "warning",
                                        "message": format!("Forbidden import for {} mod: {}", environment, pattern),
                                    }));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Check for common issues
    let src_dir = std::path::Path::new(&project_path).join("src");
    if src_dir.exists() {
        for entry in walkdir::WalkDir::new(&src_dir).into_iter().flatten() {
            let path = entry.path();
            if !path.is_file() || path.extension().and_then(|e| e.to_str()) != Some("java") {
                continue;
            }
            if let Ok(content) = std::fs::read_to_string(path) {
                for (line_num, line) in content.lines().enumerate() {
                    let trimmed = line.trim();
                    // Check for star imports
                    if trimmed.starts_with("import ") && trimmed.contains(".*") {
                        diagnostics.push(json!({
                            "file": path.to_string_lossy(),
                            "line": line_num + 1,
                            "severity": "warning",
                            "message": "Star import detected — use explicit imports",
                        }));
                    }
                    // Check for direct Minecraft class usage
                    if trimmed.starts_with("import net.minecraft.") {
                        diagnostics.push(json!({
                            "file": path.to_string_lossy(),
                            "line": line_num + 1,
                            "severity": "error",
                            "message": "Direct Minecraft import — use Alloy API instead",
                        }));
                    }
                }
            }
        }
    }

    ToolResult::json(&json!({
        "environment": environment,
        "diagnostics": diagnostics,
        "count": diagnostics.len()
    }))
}

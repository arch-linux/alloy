use crate::lsp::manager::LspManager;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, State};

/// LSP state managed by Tauri
pub struct LspState {
    pub manager: LspManager,
}

impl LspState {
    pub fn new() -> Self {
        Self {
            manager: LspManager::new(),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct CompletionItem {
    pub label: String,
    pub kind: String,
    pub detail: Option<String>,
    pub insert_text: Option<String>,
    pub sort_text: Option<String>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct HoverResult {
    pub contents: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct LocationResult {
    pub uri: String,
    pub line: u32,
    pub character: u32,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct Diagnostic {
    pub path: String,
    pub line: u32,
    pub character: u32,
    pub end_line: u32,
    pub end_character: u32,
    pub severity: String,
    pub message: String,
    pub source: Option<String>,
}

/// Start the LSP server for the given project.
#[tauri::command]
pub async fn lsp_start(
    project_path: String,
    lsp_state: State<'_, LspState>,
    app: AppHandle,
) -> Result<(), String> {
    let manager = &lsp_state.manager;
    manager.start(&project_path).await?;

    // Spawn a task to forward diagnostics notifications to the frontend
    if let Some(mut rx) = manager.take_notification_rx().await {
        let app_handle = app.clone();
        tokio::spawn(async move {
            while let Some(msg) = rx.recv().await {
                if let Some(method) = &msg.method {
                    match method.as_str() {
                        "textDocument/publishDiagnostics" => {
                            if let Some(params) = &msg.params {
                                let diagnostics = parse_diagnostics(params);
                                let _ = app_handle.emit("lsp:diagnostics", &diagnostics);
                            }
                        }
                        "window/logMessage" | "window/showMessage" => {
                            if let Some(params) = &msg.params {
                                let message = params
                                    .get("message")
                                    .and_then(|m| m.as_str())
                                    .unwrap_or("");
                                let _ = app_handle.emit("lsp:log", message);
                            }
                        }
                        _ => {}
                    }
                }
            }
        });
    }

    let _ = app.emit("lsp:status", "running");
    Ok(())
}

/// Stop the LSP server.
#[tauri::command]
pub async fn lsp_stop(
    lsp_state: State<'_, LspState>,
    app: AppHandle,
) -> Result<(), String> {
    lsp_state.manager.stop().await?;
    let _ = app.emit("lsp:status", "stopped");
    Ok(())
}

/// Get the current LSP status.
#[tauri::command]
pub async fn lsp_status(lsp_state: State<'_, LspState>) -> Result<String, String> {
    let status = lsp_state.manager.status().await;
    Ok(format!("{:?}", status))
}

/// Notify LSP that a file was opened.
#[tauri::command]
pub async fn lsp_did_open(
    path: String,
    language_id: String,
    text: String,
    lsp_state: State<'_, LspState>,
) -> Result<(), String> {
    let uri = path_to_uri(&path);
    lsp_state
        .manager
        .did_open(&uri, &language_id, &text)
        .await
}

/// Notify LSP that a file changed.
#[tauri::command]
pub async fn lsp_did_change(
    path: String,
    version: i32,
    text: String,
    lsp_state: State<'_, LspState>,
) -> Result<(), String> {
    let uri = path_to_uri(&path);
    lsp_state
        .manager
        .did_change(&uri, version, &text)
        .await
}

/// Notify LSP that a file was closed.
#[tauri::command]
pub async fn lsp_did_close(
    path: String,
    lsp_state: State<'_, LspState>,
) -> Result<(), String> {
    let uri = path_to_uri(&path);
    lsp_state.manager.did_close(&uri).await
}

/// Notify LSP that a file was saved.
#[tauri::command]
pub async fn lsp_did_save(
    path: String,
    text: String,
    lsp_state: State<'_, LspState>,
) -> Result<(), String> {
    let uri = path_to_uri(&path);
    lsp_state.manager.did_save(&uri, &text).await
}

/// Get completions at a position.
#[tauri::command]
pub async fn lsp_completion(
    path: String,
    line: u32,
    character: u32,
    lsp_state: State<'_, LspState>,
) -> Result<Vec<CompletionItem>, String> {
    let uri = path_to_uri(&path);
    let result = lsp_state
        .manager
        .completion(&uri, line, character)
        .await?;

    Ok(parse_completions(&result))
}

/// Get hover info at a position.
#[tauri::command]
pub async fn lsp_hover(
    path: String,
    line: u32,
    character: u32,
    lsp_state: State<'_, LspState>,
) -> Result<Option<HoverResult>, String> {
    let uri = path_to_uri(&path);
    let result = lsp_state.manager.hover(&uri, line, character).await?;

    if result.is_null() {
        return Ok(None);
    }

    let contents = if let Some(contents) = result.get("contents") {
        extract_hover_contents(contents)
    } else {
        String::new()
    };

    if contents.is_empty() {
        Ok(None)
    } else {
        Ok(Some(HoverResult { contents }))
    }
}

/// Go to definition.
#[tauri::command]
pub async fn lsp_definition(
    path: String,
    line: u32,
    character: u32,
    lsp_state: State<'_, LspState>,
) -> Result<Vec<LocationResult>, String> {
    let uri = path_to_uri(&path);
    let result = lsp_state
        .manager
        .definition(&uri, line, character)
        .await?;

    Ok(parse_locations(&result))
}

/// Find references.
#[tauri::command]
pub async fn lsp_references(
    path: String,
    line: u32,
    character: u32,
    lsp_state: State<'_, LspState>,
) -> Result<Vec<LocationResult>, String> {
    let uri = path_to_uri(&path);
    let result = lsp_state
        .manager
        .references(&uri, line, character)
        .await?;

    Ok(parse_locations(&result))
}

/// Search workspace symbols.
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WorkspaceSymbol {
    pub name: String,
    pub kind: String,
    pub path: String,
    pub line: u32,
    pub character: u32,
    pub container_name: Option<String>,
}

#[tauri::command]
pub async fn lsp_workspace_symbols(
    query: String,
    lsp_state: State<'_, LspState>,
) -> Result<Vec<WorkspaceSymbol>, String> {
    let result = lsp_state.manager.workspace_symbols(&query).await?;

    let symbols = result.as_array().cloned().unwrap_or_default();
    Ok(symbols
        .iter()
        .filter_map(|s| {
            let name = s.get("name")?.as_str()?.to_string();
            let kind = s
                .get("kind")
                .and_then(|k| k.as_u64())
                .map(symbol_kind_to_string)
                .unwrap_or_else(|| "unknown".to_string());
            let location = s.get("location")?;
            let uri = location.get("uri")?.as_str()?;
            let range = location.get("range")?;
            let start = range.get("start")?;
            let line = start.get("line")?.as_u64()? as u32;
            let character = start.get("character")?.as_u64()? as u32;
            let container_name = s
                .get("containerName")
                .and_then(|c| c.as_str())
                .map(String::from);

            Some(WorkspaceSymbol {
                name,
                kind,
                path: uri_to_path(uri),
                line,
                character,
                container_name,
            })
        })
        .collect())
}

/// Get signature help for method parameters.
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SignatureHelp {
    pub signatures: Vec<SignatureInfo>,
    pub active_signature: u32,
    pub active_parameter: u32,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct SignatureInfo {
    pub label: String,
    pub parameters: Vec<String>,
}

#[tauri::command]
pub async fn lsp_signature_help(
    path: String,
    line: u32,
    character: u32,
    lsp_state: State<'_, LspState>,
) -> Result<Option<SignatureHelp>, String> {
    let uri = path_to_uri(&path);
    let result = lsp_state
        .manager
        .signature_help(&uri, line, character)
        .await?;

    if result.is_null() {
        return Ok(None);
    }

    let signatures: Vec<SignatureInfo> = result
        .get("signatures")
        .and_then(|s| s.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|sig| {
                    let label = sig.get("label")?.as_str()?.to_string();
                    let parameters = sig
                        .get("parameters")
                        .and_then(|p| p.as_array())
                        .map(|params| {
                            params
                                .iter()
                                .filter_map(|p| {
                                    p.get("label")
                                        .and_then(|l| l.as_str())
                                        .map(String::from)
                                })
                                .collect()
                        })
                        .unwrap_or_default();
                    Some(SignatureInfo { label, parameters })
                })
                .collect()
        })
        .unwrap_or_default();

    if signatures.is_empty() {
        return Ok(None);
    }

    let active_signature = result
        .get("activeSignature")
        .and_then(|s| s.as_u64())
        .unwrap_or(0) as u32;
    let active_parameter = result
        .get("activeParameter")
        .and_then(|p| p.as_u64())
        .unwrap_or(0) as u32;

    Ok(Some(SignatureHelp {
        signatures,
        active_signature,
        active_parameter,
    }))
}

/// Get document symbols for outline view.
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct DocumentSymbol {
    pub name: String,
    pub kind: String,
    pub line: u32,
    pub character: u32,
    pub end_line: u32,
    pub children: Vec<DocumentSymbol>,
}

#[tauri::command]
pub async fn lsp_document_symbols(
    path: String,
    lsp_state: State<'_, LspState>,
) -> Result<Vec<DocumentSymbol>, String> {
    let uri = path_to_uri(&path);
    let result = lsp_state.manager.document_symbols(&uri).await?;
    Ok(parse_document_symbols(&result))
}

/// Get code actions (quick fixes) at a range.
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct CodeAction {
    pub title: String,
    pub kind: Option<String>,
    pub edits: Vec<WorkspaceEdit>,
}

#[tauri::command]
pub async fn lsp_code_actions(
    path: String,
    start_line: u32,
    start_character: u32,
    end_line: u32,
    end_character: u32,
    lsp_state: State<'_, LspState>,
) -> Result<Vec<CodeAction>, String> {
    let uri = path_to_uri(&path);
    let result = lsp_state
        .manager
        .code_actions(
            &uri,
            start_line,
            start_character,
            end_line,
            end_character,
            vec![],
        )
        .await?;

    Ok(parse_code_actions(&result))
}

/// Rename a symbol across the workspace.
#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct WorkspaceEdit {
    pub path: String,
    pub edits: Vec<TextEdit>,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct TextEdit {
    pub start_line: u32,
    pub start_character: u32,
    pub end_line: u32,
    pub end_character: u32,
    pub new_text: String,
}

#[tauri::command]
pub async fn lsp_rename(
    path: String,
    line: u32,
    character: u32,
    new_name: String,
    lsp_state: State<'_, LspState>,
) -> Result<Vec<WorkspaceEdit>, String> {
    let uri = path_to_uri(&path);
    let result = lsp_state
        .manager
        .rename(&uri, line, character, &new_name)
        .await?;

    Ok(parse_workspace_edit(&result))
}

/// Check if rename is valid at the given position.
#[tauri::command]
pub async fn lsp_prepare_rename(
    path: String,
    line: u32,
    character: u32,
    lsp_state: State<'_, LspState>,
) -> Result<Option<String>, String> {
    let uri = path_to_uri(&path);
    let result = lsp_state
        .manager
        .prepare_rename(&uri, line, character)
        .await?;

    if result.is_null() {
        return Ok(None);
    }

    // Extract the placeholder (current name)
    let placeholder = result
        .get("placeholder")
        .and_then(|p| p.as_str())
        .map(String::from);

    Ok(placeholder)
}

// --- Helpers ---

fn path_to_uri(path: &str) -> String {
    format!("file://{}", path)
}

fn uri_to_path(uri: &str) -> String {
    uri.strip_prefix("file://").unwrap_or(uri).to_string()
}

fn parse_completions(value: &serde_json::Value) -> Vec<CompletionItem> {
    let items = if let Some(arr) = value.as_array() {
        arr.clone()
    } else if let Some(items) = value.get("items").and_then(|v| v.as_array()) {
        items.clone()
    } else {
        return Vec::new();
    };

    items
        .iter()
        .filter_map(|item| {
            let label = item.get("label")?.as_str()?.to_string();
            let kind = item
                .get("kind")
                .and_then(|k| k.as_u64())
                .map(completion_kind_to_string)
                .unwrap_or_else(|| "text".to_string());
            let detail = item.get("detail").and_then(|d| d.as_str()).map(String::from);
            let insert_text = item
                .get("insertText")
                .or_else(|| item.get("textEdit").and_then(|te| te.get("newText")))
                .and_then(|t| t.as_str())
                .map(String::from);
            let sort_text = item
                .get("sortText")
                .and_then(|s| s.as_str())
                .map(String::from);

            Some(CompletionItem {
                label,
                kind,
                detail,
                insert_text,
                sort_text,
            })
        })
        .collect()
}

fn completion_kind_to_string(kind: u64) -> String {
    match kind {
        1 => "text",
        2 => "method",
        3 => "function",
        4 => "constructor",
        5 => "field",
        6 => "variable",
        7 => "class",
        8 => "interface",
        9 => "module",
        10 => "property",
        11 => "unit",
        12 => "value",
        13 => "enum",
        14 => "keyword",
        15 => "snippet",
        16 => "color",
        17 => "file",
        18 => "reference",
        19 => "folder",
        20 => "enum_member",
        21 => "constant",
        22 => "struct",
        23 => "event",
        24 => "operator",
        25 => "type_parameter",
        _ => "text",
    }
    .to_string()
}

fn extract_hover_contents(contents: &serde_json::Value) -> String {
    if let Some(s) = contents.as_str() {
        return s.to_string();
    }

    if let Some(obj) = contents.as_object() {
        if let Some(value) = obj.get("value").and_then(|v| v.as_str()) {
            return value.to_string();
        }
    }

    if let Some(arr) = contents.as_array() {
        return arr
            .iter()
            .filter_map(|v| {
                if let Some(s) = v.as_str() {
                    Some(s.to_string())
                } else if let Some(obj) = v.as_object() {
                    obj.get("value").and_then(|v| v.as_str()).map(String::from)
                } else {
                    None
                }
            })
            .collect::<Vec<_>>()
            .join("\n\n");
    }

    String::new()
}

fn parse_locations(value: &serde_json::Value) -> Vec<LocationResult> {
    let locations = if let Some(arr) = value.as_array() {
        arr.clone()
    } else if value.is_object() {
        vec![value.clone()]
    } else {
        return Vec::new();
    };

    locations
        .iter()
        .filter_map(|loc| {
            let uri = loc.get("uri")?.as_str()?;
            let range = loc.get("range")?;
            let start = range.get("start")?;
            let line = start.get("line")?.as_u64()? as u32;
            let character = start.get("character")?.as_u64()? as u32;

            Some(LocationResult {
                uri: uri_to_path(uri),
                line,
                character,
            })
        })
        .collect()
}

fn parse_document_symbols(value: &serde_json::Value) -> Vec<DocumentSymbol> {
    let symbols = match value.as_array() {
        Some(arr) => arr,
        None => return Vec::new(),
    };

    symbols
        .iter()
        .filter_map(|s| parse_single_symbol(s))
        .collect()
}

fn parse_single_symbol(value: &serde_json::Value) -> Option<DocumentSymbol> {
    let name = value.get("name")?.as_str()?.to_string();
    let kind = value
        .get("kind")
        .and_then(|k| k.as_u64())
        .map(symbol_kind_to_string)
        .unwrap_or_else(|| "unknown".to_string());

    // DocumentSymbol has "range", SymbolInformation has "location"
    let (line, character, end_line) = if let Some(range) = value.get("range") {
        let start = range.get("start")?;
        let end = range.get("end")?;
        (
            start.get("line")?.as_u64()? as u32,
            start.get("character")?.as_u64()? as u32,
            end.get("line")?.as_u64()? as u32,
        )
    } else if let Some(loc) = value.get("location") {
        let range = loc.get("range")?;
        let start = range.get("start")?;
        let end = range.get("end")?;
        (
            start.get("line")?.as_u64()? as u32,
            start.get("character")?.as_u64()? as u32,
            end.get("line")?.as_u64()? as u32,
        )
    } else {
        return None;
    };

    let children = value
        .get("children")
        .and_then(|c| c.as_array())
        .map(|arr| arr.iter().filter_map(parse_single_symbol).collect())
        .unwrap_or_default();

    Some(DocumentSymbol {
        name,
        kind,
        line,
        character,
        end_line,
        children,
    })
}

fn symbol_kind_to_string(kind: u64) -> String {
    match kind {
        1 => "file",
        2 => "module",
        3 => "namespace",
        4 => "package",
        5 => "class",
        6 => "method",
        7 => "property",
        8 => "field",
        9 => "constructor",
        10 => "enum",
        11 => "interface",
        12 => "function",
        13 => "variable",
        14 => "constant",
        15 => "string",
        16 => "number",
        17 => "boolean",
        18 => "array",
        19 => "object",
        20 => "key",
        21 => "null",
        22 => "enum_member",
        23 => "struct",
        24 => "event",
        25 => "operator",
        26 => "type_parameter",
        _ => "unknown",
    }
    .to_string()
}

fn parse_code_actions(value: &serde_json::Value) -> Vec<CodeAction> {
    let actions = match value.as_array() {
        Some(arr) => arr,
        None => return Vec::new(),
    };

    actions
        .iter()
        .filter_map(|action| {
            let title = action.get("title")?.as_str()?.to_string();
            let kind = action
                .get("kind")
                .and_then(|k| k.as_str())
                .map(String::from);

            let edits = if let Some(edit) = action.get("edit") {
                parse_workspace_edit(edit)
            } else {
                Vec::new()
            };

            Some(CodeAction { title, kind, edits })
        })
        .collect()
}

fn parse_workspace_edit(value: &serde_json::Value) -> Vec<WorkspaceEdit> {
    let changes = match value.get("changes") {
        Some(c) if c.is_object() => c.as_object().unwrap(),
        _ => return Vec::new(),
    };

    changes
        .iter()
        .map(|(uri, edits_val)| {
            let path = uri_to_path(uri);
            let edits = edits_val
                .as_array()
                .map(|arr| {
                    arr.iter()
                        .filter_map(|edit| {
                            let range = edit.get("range")?;
                            let start = range.get("start")?;
                            let end = range.get("end")?;
                            let new_text =
                                edit.get("newText")?.as_str()?.to_string();

                            Some(TextEdit {
                                start_line: start.get("line")?.as_u64()? as u32,
                                start_character: start.get("character")?.as_u64()? as u32,
                                end_line: end.get("line")?.as_u64()? as u32,
                                end_character: end.get("character")?.as_u64()? as u32,
                                new_text,
                            })
                        })
                        .collect()
                })
                .unwrap_or_default();

            WorkspaceEdit { path, edits }
        })
        .collect()
}

fn parse_diagnostics(params: &serde_json::Value) -> Vec<Diagnostic> {
    let uri = params
        .get("uri")
        .and_then(|u| u.as_str())
        .unwrap_or("");
    let path = uri_to_path(uri);

    let diagnostics = params
        .get("diagnostics")
        .and_then(|d| d.as_array())
        .cloned()
        .unwrap_or_default();

    diagnostics
        .iter()
        .filter_map(|d| {
            let range = d.get("range")?;
            let start = range.get("start")?;
            let end = range.get("end")?;
            let severity = d
                .get("severity")
                .and_then(|s| s.as_u64())
                .map(|s| match s {
                    1 => "error",
                    2 => "warning",
                    3 => "info",
                    4 => "hint",
                    _ => "info",
                })
                .unwrap_or("info")
                .to_string();
            let message = d.get("message")?.as_str()?.to_string();
            let source = d.get("source").and_then(|s| s.as_str()).map(String::from);

            Some(Diagnostic {
                path: path.clone(),
                line: start.get("line")?.as_u64()? as u32,
                character: start.get("character")?.as_u64()? as u32,
                end_line: end.get("line")?.as_u64()? as u32,
                end_character: end.get("character")?.as_u64()? as u32,
                severity,
                message,
                source,
            })
        })
        .collect()
}

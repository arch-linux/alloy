use serde_json::json;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use tokio::process::Command;
use tokio::sync::{mpsc, Mutex, RwLock};

use super::transport::{LspMessage, LspTransport};

/// Represents the current state of the LSP server.
#[derive(Debug, Clone, PartialEq)]
pub enum LspStatus {
    Stopped,
    Starting,
    Running,
    Error(String),
}

/// Manages the Eclipse JDT Language Server lifecycle and communication.
pub struct LspManager {
    transport: RwLock<Option<Arc<LspTransport>>>,
    status: RwLock<LspStatus>,
    project_root: RwLock<Option<String>>,
    /// Channel for notifications from the LSP server (diagnostics, etc.)
    notification_rx: Mutex<Option<mpsc::UnboundedReceiver<LspMessage>>>,
}

impl LspManager {
    pub fn new() -> Self {
        Self {
            transport: RwLock::new(None),
            status: RwLock::new(LspStatus::Stopped),
            project_root: RwLock::new(None),
            notification_rx: Mutex::new(None),
        }
    }

    pub async fn status(&self) -> LspStatus {
        self.status.read().await.clone()
    }

    /// Find the JDT LS installation. Checks:
    /// 1. JDTLS_HOME environment variable
    /// 2. Bundled path relative to the app
    /// 3. Common install locations
    fn find_jdtls() -> Option<PathBuf> {
        // Check env var first
        if let Ok(home) = std::env::var("JDTLS_HOME") {
            let p = PathBuf::from(&home);
            if p.exists() {
                return Some(p);
            }
        }

        // Check common locations
        let candidates = [
            // macOS Homebrew
            "/opt/homebrew/opt/jdtls/libexec",
            "/usr/local/opt/jdtls/libexec",
            // Linux common
            "/usr/share/java/jdtls",
            "/usr/local/share/jdtls",
            // User-local
            &format!(
                "{}/.local/share/jdtls",
                std::env::var("HOME").unwrap_or_default()
            ),
        ];

        for path in &candidates {
            let p = PathBuf::from(path);
            if p.exists() {
                return Some(p);
            }
        }

        None
    }

    /// Find the Java executable.
    fn find_java() -> Option<PathBuf> {
        if let Ok(java_home) = std::env::var("JAVA_HOME") {
            let java = PathBuf::from(&java_home).join("bin/java");
            if java.exists() {
                return Some(java);
            }
        }
        // Fall back to PATH
        Some(PathBuf::from("java"))
    }

    /// Start the JDT LS for the given project root.
    pub async fn start(&self, project_root: &str) -> Result<(), String> {
        // Don't start if already running
        let status = self.status.read().await.clone();
        if status == LspStatus::Running || status == LspStatus::Starting {
            return Ok(());
        }

        *self.status.write().await = LspStatus::Starting;
        *self.project_root.write().await = Some(project_root.to_string());

        let jdtls_home = Self::find_jdtls().ok_or_else(|| {
            "Eclipse JDT LS not found. Set JDTLS_HOME or install via: brew install jdtls"
                .to_string()
        })?;

        let java = Self::find_java().ok_or_else(|| {
            "Java not found. Set JAVA_HOME or install Java 21+".to_string()
        })?;

        // Find the launcher jar
        let plugins_dir = jdtls_home.join("plugins");
        let launcher_jar = Self::find_launcher_jar(&plugins_dir)?;

        // Find the config directory based on OS
        let config_dir = Self::find_config_dir(&jdtls_home)?;

        // Create workspace data directory
        let data_dir = Self::workspace_data_dir(project_root);
        tokio::fs::create_dir_all(&data_dir)
            .await
            .map_err(|e| format!("Failed to create workspace dir: {}", e))?;

        // Build the command
        let mut cmd = Command::new(java);
        cmd.arg("-Declipse.application=org.eclipse.jdt.ls.core.id1")
            .arg("-Dosgi.bundles.defaultStartLevel=4")
            .arg("-Declipse.product=org.eclipse.jdt.ls.core.product")
            .arg("-Dlog.level=ALL")
            .arg("-Xmx512m")
            .arg("--add-modules=ALL-SYSTEM")
            .arg("--add-opens")
            .arg("java.base/java.util=ALL-UNNAMED")
            .arg("--add-opens")
            .arg("java.base/java.lang=ALL-UNNAMED")
            .arg("-jar")
            .arg(&launcher_jar)
            .arg("-configuration")
            .arg(&config_dir)
            .arg("-data")
            .arg(&data_dir)
            .stdin(std::process::Stdio::piped())
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::null());

        let mut child = cmd
            .spawn()
            .map_err(|e| format!("Failed to spawn JDT LS: {}", e))?;

        let stdin = child
            .stdin
            .take()
            .ok_or_else(|| "Failed to get JDT LS stdin".to_string())?;
        let stdout = child
            .stdout
            .take()
            .ok_or_else(|| "Failed to get JDT LS stdout".to_string())?;

        let (transport, notification_rx) = LspTransport::new(stdin, stdout);
        let transport = Arc::new(transport);

        // Send initialize request
        let init_result = transport
            .request(
                "initialize",
                Some(json!({
                    "processId": std::process::id(),
                    "rootUri": format!("file://{}", project_root),
                    "capabilities": {
                        "textDocument": {
                            "completion": {
                                "completionItem": {
                                    "snippetSupport": true,
                                    "resolveSupport": {
                                        "properties": ["documentation", "detail"]
                                    }
                                }
                            },
                            "hover": {
                                "contentFormat": ["markdown", "plaintext"]
                            },
                            "definition": {},
                            "references": {},
                            "publishDiagnostics": {
                                "relatedInformation": true
                            },
                            "synchronization": {
                                "didSave": true,
                                "willSave": false,
                                "willSaveWaitUntil": false
                            }
                        },
                        "workspace": {
                            "workspaceFolders": true
                        }
                    },
                    "workspaceFolders": [{
                        "uri": format!("file://{}", project_root),
                        "name": Path::new(project_root)
                            .file_name()
                            .and_then(|n| n.to_str())
                            .unwrap_or("project")
                    }],
                    "initializationOptions": {
                        "settings": {
                            "java": {
                                "home": std::env::var("JAVA_HOME").ok(),
                                "configuration": {
                                    "runtimes": []
                                },
                                "import": {
                                    "gradle": {
                                        "enabled": true
                                    }
                                }
                            }
                        }
                    }
                })),
            )
            .await?;

        // Log capabilities if needed
        let _ = init_result;

        // Send initialized notification
        transport.notify("initialized", Some(json!({}))).await?;

        *self.transport.write().await = Some(transport);
        *self.notification_rx.lock().await = Some(notification_rx);
        *self.status.write().await = LspStatus::Running;

        // Spawn a task to monitor the child process
        tokio::spawn(async move {
            let _ = child.wait().await;
        });

        Ok(())
    }

    /// Stop the LSP server.
    pub async fn stop(&self) -> Result<(), String> {
        if let Some(transport) = self.transport.read().await.as_ref() {
            // Send shutdown request
            let _ = transport.request("shutdown", None).await;
            // Send exit notification
            let _ = transport.notify("exit", None).await;
        }

        *self.transport.write().await = None;
        *self.notification_rx.lock().await = None;
        *self.status.write().await = LspStatus::Stopped;
        *self.project_root.write().await = None;

        Ok(())
    }

    /// Get a reference to the transport for sending requests.
    pub async fn transport(&self) -> Option<Arc<LspTransport>> {
        self.transport.read().await.clone()
    }

    /// Take the notification receiver (can only be taken once).
    pub async fn take_notification_rx(&self) -> Option<mpsc::UnboundedReceiver<LspMessage>> {
        self.notification_rx.lock().await.take()
    }

    // --- Notification helpers ---

    /// Notify the LSP that a file was opened.
    pub async fn did_open(&self, uri: &str, language_id: &str, text: &str) -> Result<(), String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .notify(
                "textDocument/didOpen",
                Some(json!({
                    "textDocument": {
                        "uri": uri,
                        "languageId": language_id,
                        "version": 1,
                        "text": text
                    }
                })),
            )
            .await
    }

    /// Notify the LSP that a file was changed.
    pub async fn did_change(
        &self,
        uri: &str,
        version: i32,
        text: &str,
    ) -> Result<(), String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .notify(
                "textDocument/didChange",
                Some(json!({
                    "textDocument": {
                        "uri": uri,
                        "version": version
                    },
                    "contentChanges": [{
                        "text": text
                    }]
                })),
            )
            .await
    }

    /// Notify the LSP that a file was closed.
    pub async fn did_close(&self, uri: &str) -> Result<(), String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .notify(
                "textDocument/didClose",
                Some(json!({
                    "textDocument": {
                        "uri": uri
                    }
                })),
            )
            .await
    }

    /// Notify the LSP that a file was saved.
    pub async fn did_save(&self, uri: &str, text: &str) -> Result<(), String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .notify(
                "textDocument/didSave",
                Some(json!({
                    "textDocument": {
                        "uri": uri
                    },
                    "text": text
                })),
            )
            .await
    }

    // --- Request helpers ---

    /// Get completions at a position.
    pub async fn completion(
        &self,
        uri: &str,
        line: u32,
        character: u32,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "textDocument/completion",
                Some(json!({
                    "textDocument": { "uri": uri },
                    "position": { "line": line, "character": character }
                })),
            )
            .await
    }

    /// Get hover information at a position.
    pub async fn hover(
        &self,
        uri: &str,
        line: u32,
        character: u32,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "textDocument/hover",
                Some(json!({
                    "textDocument": { "uri": uri },
                    "position": { "line": line, "character": character }
                })),
            )
            .await
    }

    /// Go to definition.
    pub async fn definition(
        &self,
        uri: &str,
        line: u32,
        character: u32,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "textDocument/definition",
                Some(json!({
                    "textDocument": { "uri": uri },
                    "position": { "line": line, "character": character }
                })),
            )
            .await
    }

    /// Find references.
    pub async fn references(
        &self,
        uri: &str,
        line: u32,
        character: u32,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "textDocument/references",
                Some(json!({
                    "textDocument": { "uri": uri },
                    "position": { "line": line, "character": character },
                    "context": { "includeDeclaration": true }
                })),
            )
            .await
    }

    /// Rename a symbol.
    pub async fn rename(
        &self,
        uri: &str,
        line: u32,
        character: u32,
        new_name: &str,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "textDocument/rename",
                Some(json!({
                    "textDocument": { "uri": uri },
                    "position": { "line": line, "character": character },
                    "newName": new_name
                })),
            )
            .await
    }

    /// Search workspace symbols.
    pub async fn workspace_symbols(
        &self,
        query: &str,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "workspace/symbol",
                Some(json!({
                    "query": query
                })),
            )
            .await
    }

    /// Get signature help.
    pub async fn signature_help(
        &self,
        uri: &str,
        line: u32,
        character: u32,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "textDocument/signatureHelp",
                Some(json!({
                    "textDocument": { "uri": uri },
                    "position": { "line": line, "character": character }
                })),
            )
            .await
    }

    /// Get document symbols.
    pub async fn document_symbols(
        &self,
        uri: &str,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "textDocument/documentSymbol",
                Some(json!({
                    "textDocument": { "uri": uri }
                })),
            )
            .await
    }

    /// Get code actions at a range.
    pub async fn code_actions(
        &self,
        uri: &str,
        start_line: u32,
        start_character: u32,
        end_line: u32,
        end_character: u32,
        diagnostics: Vec<serde_json::Value>,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "textDocument/codeAction",
                Some(json!({
                    "textDocument": { "uri": uri },
                    "range": {
                        "start": { "line": start_line, "character": start_character },
                        "end": { "line": end_line, "character": end_character }
                    },
                    "context": {
                        "diagnostics": diagnostics
                    }
                })),
            )
            .await
    }

    /// Prepare rename — check if rename is valid at position.
    pub async fn prepare_rename(
        &self,
        uri: &str,
        line: u32,
        character: u32,
    ) -> Result<serde_json::Value, String> {
        let transport = self
            .transport()
            .await
            .ok_or("LSP not running".to_string())?;
        transport
            .request(
                "textDocument/prepareRename",
                Some(json!({
                    "textDocument": { "uri": uri },
                    "position": { "line": line, "character": character }
                })),
            )
            .await
    }

    // --- Internal helpers ---

    fn find_launcher_jar(plugins_dir: &Path) -> Result<PathBuf, String> {
        if !plugins_dir.exists() {
            return Err(format!("JDT LS plugins directory not found: {:?}", plugins_dir));
        }

        let entries = std::fs::read_dir(plugins_dir)
            .map_err(|e| format!("Failed to read plugins dir: {}", e))?;

        for entry in entries.flatten() {
            let name = entry.file_name();
            let name_str = name.to_string_lossy();
            if name_str.starts_with("org.eclipse.equinox.launcher_") && name_str.ends_with(".jar")
            {
                return Ok(entry.path());
            }
        }

        Err("Equinox launcher JAR not found in JDT LS plugins directory".to_string())
    }

    fn find_config_dir(jdtls_home: &Path) -> Result<PathBuf, String> {
        let os_config = if cfg!(target_os = "macos") {
            "config_mac"
        } else if cfg!(target_os = "windows") {
            "config_win"
        } else {
            "config_linux"
        };

        let config = jdtls_home.join(os_config);
        if config.exists() {
            return Ok(config);
        }

        // Fallback to generic config
        let config = jdtls_home.join("config");
        if config.exists() {
            return Ok(config);
        }

        Err(format!(
            "JDT LS config directory not found (tried {} and config)",
            os_config
        ))
    }

    fn workspace_data_dir(project_root: &str) -> PathBuf {
        let home = std::env::var("HOME").unwrap_or_else(|_| "/tmp".to_string());
        let hash = Self::simple_hash(project_root);
        PathBuf::from(home)
            .join(".alloy-ide")
            .join("jdtls-workspace")
            .join(format!("{:x}", hash))
    }

    fn simple_hash(s: &str) -> u64 {
        let mut hash: u64 = 5381;
        for byte in s.bytes() {
            hash = hash.wrapping_mul(33).wrapping_add(byte as u64);
        }
        hash
    }
}

use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::process::{ChildStdin, ChildStdout};
use tokio::sync::{mpsc, oneshot, Mutex};

/// A JSON-RPC request/response ID
pub type RequestId = i64;

/// JSON-RPC message sent to the LSP server
#[derive(Debug, Serialize)]
pub struct LspRequest {
    pub jsonrpc: String,
    pub id: RequestId,
    pub method: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub params: Option<serde_json::Value>,
}

/// JSON-RPC notification (no id, no response expected)
#[derive(Debug, Serialize)]
pub struct LspNotification {
    pub jsonrpc: String,
    pub method: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub params: Option<serde_json::Value>,
}

/// An incoming JSON-RPC message from the LSP server
#[derive(Debug, Deserialize)]
pub struct LspMessage {
    #[serde(default)]
    pub id: Option<serde_json::Value>,
    #[serde(default)]
    pub method: Option<String>,
    #[serde(default)]
    pub result: Option<serde_json::Value>,
    #[serde(default)]
    pub error: Option<serde_json::Value>,
    #[serde(default)]
    pub params: Option<serde_json::Value>,
}

impl LspMessage {
    pub fn is_response(&self) -> bool {
        self.id.is_some() && self.method.is_none()
    }

    pub fn is_notification(&self) -> bool {
        self.id.is_none() && self.method.is_some()
    }

    pub fn is_request(&self) -> bool {
        self.id.is_some() && self.method.is_some()
    }
}

type PendingRequests = Arc<Mutex<HashMap<RequestId, oneshot::Sender<LspMessage>>>>;

/// Manages bidirectional JSON-RPC communication with an LSP server over stdio.
pub struct LspTransport {
    writer: Arc<Mutex<ChildStdin>>,
    next_id: AtomicI64,
    pending: PendingRequests,
    #[allow(dead_code)]
    notification_tx: mpsc::UnboundedSender<LspMessage>,
}

impl LspTransport {
    /// Create a new transport. Spawns a reader task that routes incoming messages.
    /// Returns the transport and a receiver for server-initiated notifications.
    pub fn new(
        stdin: ChildStdin,
        stdout: ChildStdout,
    ) -> (Self, mpsc::UnboundedReceiver<LspMessage>) {
        let (notification_tx, notification_rx) = mpsc::unbounded_channel();
        let pending: PendingRequests = Arc::new(Mutex::new(HashMap::new()));

        let transport = Self {
            writer: Arc::new(Mutex::new(stdin)),
            next_id: AtomicI64::new(1),
            pending: pending.clone(),
            notification_tx: notification_tx.clone(),
        };

        // Spawn reader task
        tokio::spawn(Self::reader_loop(stdout, pending, notification_tx));

        (transport, notification_rx)
    }

    /// Send a request and wait for the response.
    pub async fn request(
        &self,
        method: &str,
        params: Option<serde_json::Value>,
    ) -> Result<serde_json::Value, String> {
        let id = self.next_id.fetch_add(1, Ordering::SeqCst);

        let req = LspRequest {
            jsonrpc: "2.0".to_string(),
            id,
            method: method.to_string(),
            params,
        };

        let (tx, rx) = oneshot::channel();
        self.pending.lock().await.insert(id, tx);

        self.send_raw(&serde_json::to_string(&req).map_err(|e| e.to_string())?)
            .await?;

        let response = rx.await.map_err(|_| "LSP response channel closed".to_string())?;

        if let Some(err) = response.error {
            Err(format!("LSP error: {}", err))
        } else {
            Ok(response.result.unwrap_or(serde_json::Value::Null))
        }
    }

    /// Send a notification (no response expected).
    pub async fn notify(
        &self,
        method: &str,
        params: Option<serde_json::Value>,
    ) -> Result<(), String> {
        let notif = LspNotification {
            jsonrpc: "2.0".to_string(),
            method: method.to_string(),
            params,
        };

        self.send_raw(&serde_json::to_string(&notif).map_err(|e| e.to_string())?)
            .await
    }

    /// Send a raw JSON-RPC message with Content-Length header framing.
    async fn send_raw(&self, json: &str) -> Result<(), String> {
        let msg = format!("Content-Length: {}\r\n\r\n{}", json.len(), json);
        let mut writer = self.writer.lock().await;
        writer
            .write_all(msg.as_bytes())
            .await
            .map_err(|e| format!("Failed to write to LSP: {}", e))?;
        writer
            .flush()
            .await
            .map_err(|e| format!("Failed to flush LSP: {}", e))?;
        Ok(())
    }

    /// Read loop that parses incoming LSP messages and routes them.
    async fn reader_loop(
        stdout: ChildStdout,
        pending: PendingRequests,
        notification_tx: mpsc::UnboundedSender<LspMessage>,
    ) {
        let mut reader = BufReader::new(stdout);

        loop {
            // Read headers until empty line
            let mut content_length: Option<usize> = None;
            loop {
                let mut header_line = String::new();
                match reader.read_line(&mut header_line).await {
                    Ok(0) => return, // EOF
                    Ok(_) => {}
                    Err(_) => return,
                }
                let trimmed = header_line.trim();
                if trimmed.is_empty() {
                    break;
                }
                if let Some(val) = trimmed.strip_prefix("Content-Length: ") {
                    content_length = val.parse().ok();
                }
            }

            let length = match content_length {
                Some(l) => l,
                None => continue,
            };

            // Read body
            let mut body = vec![0u8; length];
            if reader.read_exact(&mut body).await.is_err() {
                return;
            }

            let body_str = match String::from_utf8(body) {
                Ok(s) => s,
                Err(_) => continue,
            };

            let msg: LspMessage = match serde_json::from_str(&body_str) {
                Ok(m) => m,
                Err(_) => continue,
            };

            if msg.is_response() {
                // Route response to pending request
                if let Some(serde_json::Value::Number(n)) = &msg.id {
                    if let Some(id) = n.as_i64() {
                        let mut pending = pending.lock().await;
                        if let Some(tx) = pending.remove(&id) {
                            let _ = tx.send(msg);
                        }
                    }
                }
            } else {
                // Notification or server request → forward to notification channel
                let _ = notification_tx.send(msg);
            }
        }
    }
}

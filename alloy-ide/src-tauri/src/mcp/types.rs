use serde::{Deserialize, Serialize};

/// A single tool definition for both MCP server and Claude API.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolDefinition {
    pub name: String,
    pub description: String,
    pub input_schema: serde_json::Value,
}

/// Content block in a tool result.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum ContentBlock {
    #[serde(rename = "text")]
    Text { text: String },
}

/// Result returned by a tool execution.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolResult {
    pub content: Vec<ContentBlock>,
    #[serde(default)]
    pub is_error: bool,
}

impl ToolResult {
    pub fn text(text: impl Into<String>) -> Self {
        Self {
            content: vec![ContentBlock::Text { text: text.into() }],
            is_error: false,
        }
    }

    pub fn error(text: impl Into<String>) -> Self {
        Self {
            content: vec![ContentBlock::Text { text: text.into() }],
            is_error: true,
        }
    }

    pub fn json<T: Serialize>(value: &T) -> Self {
        match serde_json::to_string_pretty(value) {
            Ok(s) => Self::text(s),
            Err(e) => Self::error(format!("Serialization error: {}", e)),
        }
    }
}

/// Chat message role.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum ChatRole {
    User,
    Assistant,
}

/// A tool call made by the assistant.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ToolCall {
    pub id: String,
    pub name: String,
    pub input: serde_json::Value,
    #[serde(default)]
    pub status: ToolCallStatus,
    pub result: Option<String>,
}

/// Status of a tool call execution.
#[derive(Debug, Clone, Serialize, Deserialize, Default, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum ToolCallStatus {
    #[default]
    Running,
    Done,
    Error,
}

/// A single message in the AI chat.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ChatMessage {
    pub role: ChatRole,
    pub content: String,
    #[serde(default)]
    pub tool_calls: Vec<ToolCall>,
    pub timestamp: u64,
}

/// AI configuration stored in app state.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AiConfig {
    pub api_key: Option<String>,
    pub model: String,
    pub max_tokens: u32,
}

impl Default for AiConfig {
    fn default() -> Self {
        Self {
            api_key: None,
            model: "claude-sonnet-4-5-20250929".to_string(),
            max_tokens: 4096,
        }
    }
}

/// Events emitted to frontend during AI interaction.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum AiEvent {
    #[serde(rename = "ai:response-chunk")]
    ResponseChunk { text: String },
    #[serde(rename = "ai:response-done")]
    ResponseDone { message: ChatMessage },
    #[serde(rename = "ai:tool-start")]
    ToolStart { tool_call: ToolCall },
    #[serde(rename = "ai:tool-done")]
    ToolDone { tool_call: ToolCall },
    #[serde(rename = "ai:error")]
    Error { error: String },
}

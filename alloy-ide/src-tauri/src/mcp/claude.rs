use serde_json::{json, Value};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter};

use crate::state::AppState;
use super::tools::ToolRegistry;
use super::types::{ChatMessage, ChatRole, ContentBlock, ToolCall, ToolCallStatus};

const ANTHROPIC_API_URL: &str = "https://api.anthropic.com/v1/messages";
const SYSTEM_PROMPT: &str = r#"You are the Alloy IDE AI assistant — a helpful coding companion embedded in a Minecraft modding IDE.

You have access to tools that let you interact with the IDE: reading/writing files, opening editors, running builds, executing terminal commands, and more.

Key context:
- Alloy is a from-scratch Minecraft modding platform (not Forge or Fabric)
- Mods use the Alloy API: net.alloymc.api.*
- Mods declare environment: "client", "server", or "both" in alloy.mod.json
- Java 21+, Gradle 9.x with Kotlin DSL
- The IDE understands mod environment at every layer

When helping users:
- Be concise and practical
- Use tools proactively — read files before suggesting changes, write files to implement changes
- Respect the mod's declared environment (don't suggest client APIs for server mods)
- Generate code that follows Alloy conventions
- Explain what you're doing when using tools"#;

/// Claude API client that manages conversations and runs the agentic tool loop.
pub struct ClaudeClient {
    http: reqwest::Client,
}

impl ClaudeClient {
    pub fn new() -> Self {
        Self {
            http: reqwest::Client::new(),
        }
    }

    /// Send a user message and run the agentic loop until Claude responds with end_turn.
    pub async fn send_message(
        &self,
        user_message: &str,
        state: &Arc<AppState>,
        app_handle: &AppHandle,
    ) -> Result<ChatMessage, String> {
        let config = {
            let cfg = state.ai_config.lock().map_err(|e| e.to_string())?;
            cfg.clone()
        };

        let api_key = config
            .api_key
            .as_ref()
            .ok_or("No API key configured. Set your Anthropic API key first.")?;

        // Add user message to history
        let user_msg = ChatMessage {
            role: ChatRole::User,
            content: user_message.to_string(),
            tool_calls: vec![],
            timestamp: now(),
        };
        {
            let mut history = state.chat_history.lock().map_err(|e| e.to_string())?;
            history.push(user_msg);
        }

        // Build tool definitions for the API
        let tools: Vec<Value> = ToolRegistry::definitions()
            .iter()
            .map(|t| {
                json!({
                    "name": t.name,
                    "description": t.description,
                    "input_schema": t.input_schema,
                })
            })
            .collect();

        // Agentic loop
        loop {
            let messages = {
                let history = state.chat_history.lock().map_err(|e| e.to_string())?;
                build_api_messages(&history)
            };

            let body = json!({
                "model": config.model,
                "max_tokens": config.max_tokens,
                "system": SYSTEM_PROMPT,
                "tools": tools,
                "messages": messages,
            });

            let response = self
                .http
                .post(ANTHROPIC_API_URL)
                .header("x-api-key", api_key)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .json(&body)
                .send()
                .await
                .map_err(|e| format!("HTTP request failed: {}", e))?;

            if !response.status().is_success() {
                let status = response.status();
                let body = response.text().await.unwrap_or_default();
                let err = format!("API error ({}): {}", status, body);
                let _ = app_handle.emit("ai:error", json!({ "error": &err }));
                return Err(err);
            }

            let resp_body: Value = response
                .json()
                .await
                .map_err(|e| format!("Failed to parse response: {}", e))?;

            let stop_reason = resp_body
                .get("stop_reason")
                .and_then(|v| v.as_str())
                .unwrap_or("end_turn");

            let content_blocks = resp_body
                .get("content")
                .and_then(|v| v.as_array())
                .cloned()
                .unwrap_or_default();

            // Collect text and tool_use blocks
            let mut assistant_text = String::new();
            let mut tool_uses: Vec<(String, String, Value)> = Vec::new();

            for block in &content_blocks {
                match block.get("type").and_then(|v| v.as_str()) {
                    Some("text") => {
                        if let Some(text) = block.get("text").and_then(|v| v.as_str()) {
                            assistant_text.push_str(text);
                            let _ = app_handle.emit(
                                "ai:response-chunk",
                                json!({ "text": text }),
                            );
                        }
                    }
                    Some("tool_use") => {
                        let id = block.get("id").and_then(|v| v.as_str()).unwrap_or("").to_string();
                        let name = block.get("name").and_then(|v| v.as_str()).unwrap_or("").to_string();
                        let input = block.get("input").cloned().unwrap_or(Value::Null);
                        tool_uses.push((id, name, input));
                    }
                    _ => {}
                }
            }

            if tool_uses.is_empty() {
                // No tool calls — conversation turn is done
                let assistant_msg = ChatMessage {
                    role: ChatRole::Assistant,
                    content: assistant_text.clone(),
                    tool_calls: vec![],
                    timestamp: now(),
                };

                {
                    let mut history = state.chat_history.lock().map_err(|e| e.to_string())?;
                    history.push(assistant_msg.clone());
                }

                let _ = app_handle.emit(
                    "ai:response-done",
                    json!({ "message": assistant_msg }),
                );

                return Ok(assistant_msg);
            }

            // Process tool calls
            let mut tool_call_records: Vec<ToolCall> = Vec::new();
            let mut tool_results_for_api: Vec<Value> = Vec::new();

            for (id, name, input) in &tool_uses {
                let tc = ToolCall {
                    id: id.clone(),
                    name: name.clone(),
                    input: input.clone(),
                    status: ToolCallStatus::Running,
                    result: None,
                };

                let _ = app_handle.emit("ai:tool-start", json!({ "tool_call": tc }));

                // Execute the tool
                let result = ToolRegistry::execute(name, input.clone(), state).await;

                let result_text = result
                    .content
                    .iter()
                    .map(|b| match b {
                        ContentBlock::Text { text } => text.as_str(),
                    })
                    .collect::<Vec<_>>()
                    .join("\n");

                let mut tc_done = tc.clone();
                tc_done.status = if result.is_error {
                    ToolCallStatus::Error
                } else {
                    ToolCallStatus::Done
                };
                tc_done.result = Some(result_text.clone());

                let _ = app_handle.emit("ai:tool-done", json!({ "tool_call": tc_done }));

                tool_call_records.push(tc_done);

                tool_results_for_api.push(json!({
                    "type": "tool_result",
                    "tool_use_id": id,
                    "content": result_text,
                    "is_error": result.is_error,
                }));
            }

            // Store assistant message with tool calls
            let assistant_msg = ChatMessage {
                role: ChatRole::Assistant,
                content: assistant_text.clone(),
                tool_calls: tool_call_records.clone(),
                timestamp: now(),
            };

            {
                let mut history = state.chat_history.lock().map_err(|e| e.to_string())?;

                // Push the assistant message (with tool use)
                history.push(assistant_msg);

                // Push tool results as a user message (API convention)
                history.push(ChatMessage {
                    role: ChatRole::User,
                    content: String::new(),
                    tool_calls: tool_call_records,
                    timestamp: now(),
                });
            }

            // If stop_reason is "end_turn", we're done even with tools
            if stop_reason == "end_turn" {
                let final_msg = ChatMessage {
                    role: ChatRole::Assistant,
                    content: assistant_text,
                    tool_calls: vec![],
                    timestamp: now(),
                };
                let _ = app_handle.emit(
                    "ai:response-done",
                    json!({ "message": final_msg }),
                );
                return Ok(final_msg);
            }

            // Continue the loop — Claude wants to make more tool calls or generate more text
        }
    }
}

/// Build the messages array for the Claude API from chat history.
fn build_api_messages(history: &[ChatMessage]) -> Vec<Value> {
    let mut messages: Vec<Value> = Vec::new();

    for msg in history {
        match msg.role {
            ChatRole::User => {
                if msg.tool_calls.is_empty() {
                    // Regular user message
                    messages.push(json!({
                        "role": "user",
                        "content": msg.content,
                    }));
                } else {
                    // Tool results (sent as user role per API spec)
                    let content: Vec<Value> = msg
                        .tool_calls
                        .iter()
                        .map(|tc| {
                            json!({
                                "type": "tool_result",
                                "tool_use_id": tc.id,
                                "content": tc.result.as_deref().unwrap_or(""),
                                "is_error": tc.status == ToolCallStatus::Error,
                            })
                        })
                        .collect();
                    messages.push(json!({
                        "role": "user",
                        "content": content,
                    }));
                }
            }
            ChatRole::Assistant => {
                let mut content: Vec<Value> = Vec::new();

                if !msg.content.is_empty() {
                    content.push(json!({
                        "type": "text",
                        "text": msg.content,
                    }));
                }

                for tc in &msg.tool_calls {
                    content.push(json!({
                        "type": "tool_use",
                        "id": tc.id,
                        "name": tc.name,
                        "input": tc.input,
                    }));
                }

                if !content.is_empty() {
                    messages.push(json!({
                        "role": "assistant",
                        "content": content,
                    }));
                }
            }
        }
    }

    messages
}

fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

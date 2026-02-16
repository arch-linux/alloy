use std::borrow::Cow;
use std::sync::Arc;
use rmcp::model::{
    CallToolRequestParams, CallToolResult, Content, Implementation,
    ListToolsResult, PaginatedRequestParams, ServerCapabilities, ServerInfo, Tool,
};
use rmcp::service::RequestContext;
use rmcp::{ErrorData as McpError, RoleServer, ServerHandler, ServiceExt};

use crate::state::AppState;
use super::tools::ToolRegistry;

/// MCP Server that exposes all IDE tools to external agents.
/// Implements ServerHandler directly for maximum control over the dynamic tool registry.
#[derive(Clone)]
pub struct AlloyMcpServer {
    state: Arc<AppState>,
}

impl AlloyMcpServer {
    pub fn new(state: Arc<AppState>) -> Self {
        Self { state }
    }

    /// Convert our ToolDefinitions into rmcp Tool structs.
    fn mcp_tools() -> Vec<Tool> {
        ToolRegistry::definitions()
            .into_iter()
            .map(|td| {
                let schema = match td.input_schema {
                    serde_json::Value::Object(map) => map.into_iter().collect(),
                    _ => serde_json::Map::new(),
                };
                Tool::new(
                    Cow::Owned(td.name),
                    Cow::Owned(td.description),
                    Arc::new(schema),
                )
            })
            .collect()
    }
}

impl ServerHandler for AlloyMcpServer {
    fn get_info(&self) -> ServerInfo {
        ServerInfo {
            capabilities: ServerCapabilities::builder().enable_tools().build(),
            server_info: Implementation {
                name: "alloy-ide".into(),
                version: env!("CARGO_PKG_VERSION").into(),
                title: Some("Alloy IDE MCP Server".into()),
                description: Some("MCP server for the Alloy Minecraft modding IDE".into()),
                ..Default::default()
            },
            ..Default::default()
        }
    }

    fn list_tools(
        &self,
        _request: Option<PaginatedRequestParams>,
        _context: RequestContext<RoleServer>,
    ) -> impl std::future::Future<Output = Result<ListToolsResult, McpError>> + Send + '_ {
        async move {
            Ok(ListToolsResult {
                tools: Self::mcp_tools(),
                next_cursor: None,
                meta: None,
            })
        }
    }

    fn call_tool(
        &self,
        request: CallToolRequestParams,
        _context: RequestContext<RoleServer>,
    ) -> impl std::future::Future<Output = Result<CallToolResult, McpError>> + Send + '_ {
        async move {
            let name = request.name.as_ref();
            let params = match request.arguments {
                Some(args) => serde_json::Value::Object(args),
                None => serde_json::Value::Null,
            };

            let result = ToolRegistry::execute(name, params, &self.state).await;

            let content: Vec<Content> = result
                .content
                .into_iter()
                .map(|block| match block {
                    super::types::ContentBlock::Text { text } => Content::text(text),
                })
                .collect();

            if result.is_error {
                Ok(CallToolResult::error(content))
            } else {
                Ok(CallToolResult::success(content))
            }
        }
    }

    fn get_tool(&self, name: &str) -> Option<Tool> {
        Self::mcp_tools().into_iter().find(|t| t.name == name)
    }
}

/// Spawn the MCP server listening on stdio. Called from lib.rs on startup.
pub async fn spawn_mcp_server(state: Arc<AppState>) {
    // Only start if ALLOY_MCP env var is set (for external agent connections)
    if std::env::var("ALLOY_MCP").is_err() {
        return;
    }

    let server = AlloyMcpServer::new(state);
    let transport = rmcp::transport::io::stdio();

    match server.serve(transport).await {
        Ok(ct) => {
            // Wait for it to finish
            let _ = ct.waiting().await;
        }
        Err(e) => {
            eprintln!("MCP server error: {}", e);
        }
    }
}

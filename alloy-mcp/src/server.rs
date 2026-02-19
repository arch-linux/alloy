use std::borrow::Cow;
use std::sync::Arc;
use rmcp::model::{
    CallToolRequestParams, CallToolResult, Content, Implementation,
    ListResourcesResult, ListToolsResult, PaginatedRequestParams, ReadResourceRequestParams,
    ReadResourceResult, RawResource, ResourceContents, ServerCapabilities, ServerInfo, Tool,
};
use rmcp::model::AnnotateAble;
use rmcp::service::RequestContext;
use rmcp::{ErrorData as McpError, RoleServer, ServerHandler, ServiceExt};

use crate::state::ProjectState;
use crate::tools::ToolRegistry;
use crate::tools::resources;

/// Standalone MCP Server that exposes all Alloy IDE tools to Claude.
#[derive(Clone)]
pub struct AlloyMcpServer {
    state: Arc<ProjectState>,
}

impl AlloyMcpServer {
    pub fn new(state: Arc<ProjectState>) -> Self {
        Self { state }
    }

    /// Convert ToolDefinitions into rmcp Tool structs.
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
            capabilities: ServerCapabilities::builder()
                .enable_tools()
                .enable_resources()
                .build(),
            server_info: Implementation {
                name: "alloy-mcp".into(),
                version: env!("CARGO_PKG_VERSION").into(),
                title: Some("Alloy MCP Server".into()),
                description: Some(
                    "Standalone MCP server for Alloy Minecraft modding — full project control"
                        .into(),
                ),
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
                    crate::types::ContentBlock::Text { text } => Content::text(text),
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

    fn list_resources(
        &self,
        _request: Option<PaginatedRequestParams>,
        _context: RequestContext<RoleServer>,
    ) -> impl std::future::Future<Output = Result<ListResourcesResult, McpError>> + Send + '_ {
        async move {
            let resources = vec![
                RawResource {
                    uri: "alloy://project".into(),
                    name: "Current project info".into(),
                    mime_type: Some("application/json".into()),
                    ..RawResource::new("alloy://project", "Current project info")
                }.no_annotation(),
                RawResource {
                    mime_type: Some("text/plain".into()),
                    ..RawResource::new("alloy://file-tree", "Project file tree")
                }.no_annotation(),
                RawResource {
                    mime_type: Some("application/json".into()),
                    ..RawResource::new("alloy://diagnostics", "Build errors and environment warnings")
                }.no_annotation(),
                RawResource {
                    mime_type: Some("text/markdown".into()),
                    ..RawResource::new("alloy://api-reference", "Alloy API reference")
                }.no_annotation(),
            ];
            Ok(ListResourcesResult {
                resources,
                next_cursor: None,
                meta: None,
            })
        }
    }

    fn read_resource(
        &self,
        request: ReadResourceRequestParams,
        _context: RequestContext<RoleServer>,
    ) -> impl std::future::Future<Output = Result<ReadResourceResult, McpError>> + Send + '_ {
        let state = self.state.clone();
        async move {
            let uri = request.uri.as_str();
            let content = resources::read_resource(uri, &state).await;
            Ok(ReadResourceResult {
                contents: vec![ResourceContents::text(content, uri)],
            })
        }
    }
}

/// Start the MCP server on stdio.
pub async fn run_server(state: Arc<ProjectState>) {
    let server = AlloyMcpServer::new(state);
    let transport = rmcp::transport::io::stdio();

    match server.serve(transport).await {
        Ok(ct) => {
            let _ = ct.waiting().await;
        }
        Err(e) => {
            eprintln!("MCP server error: {}", e);
            std::process::exit(1);
        }
    }
}

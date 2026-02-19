pub mod animation;
pub mod block;
pub mod build;
pub mod editor;
pub mod filesystem;
pub mod git;
pub mod gui;
pub mod lsp;
pub mod modpack;
pub mod project;
pub mod resources;
pub mod terminal;

use crate::state::ProjectState;
use crate::types::{ToolDefinition, ToolResult};
use serde_json::Value;

/// Central tool registry. Aggregates definitions and dispatch from all tool modules.
pub struct ToolRegistry;

impl ToolRegistry {
    /// Returns definitions for all available tools across every domain.
    pub fn definitions() -> Vec<ToolDefinition> {
        let mut defs = Vec::new();
        defs.extend(project::definitions());
        defs.extend(filesystem::definitions());
        defs.extend(git::definitions());
        defs.extend(editor::definitions());
        defs.extend(build::definitions());
        defs.extend(terminal::definitions());
        defs.extend(block::definitions());
        defs.extend(gui::definitions());
        defs.extend(animation::definitions());
        defs.extend(modpack::definitions());
        defs.extend(lsp::definitions());
        defs
    }

    /// Execute a tool by name with JSON parameters.
    pub async fn execute(name: &str, params: Value, state: &ProjectState) -> ToolResult {
        // Route to the correct module based on tool name prefix
        if name.starts_with("project_") {
            return project::execute(name, params, state).await;
        }
        if name.starts_with("fs_") {
            return filesystem::execute(name, params, state).await;
        }
        if name.starts_with("git_") {
            return git::execute(name, params, state).await;
        }
        if name.starts_with("editor_") {
            return editor::execute(name, params).await;
        }
        if name.starts_with("build_") {
            return build::execute(name, params, state).await;
        }
        if name.starts_with("terminal_") {
            return terminal::execute(name, params, state).await;
        }
        if name.starts_with("block_") {
            return block::execute(name, params, state).await;
        }
        if name.starts_with("gui_") {
            return gui::execute(name, params).await;
        }
        if name.starts_with("anim_") {
            return animation::execute(name, params).await;
        }
        if name.starts_with("modpack_") {
            return modpack::execute(name, params, state).await;
        }
        if name.starts_with("code_") {
            return lsp::execute(name, params, state).await;
        }

        ToolResult::error(format!("Unknown tool: {}", name))
    }
}

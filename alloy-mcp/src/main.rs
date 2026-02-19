mod server;
mod state;
mod tools;
mod types;

use clap::Parser;
use std::sync::Arc;

/// Alloy MCP Server — standalone MCP server for Alloy mod project control.
/// Gives Claude complete control over every IDE capability.
#[derive(Parser)]
#[command(name = "alloy-mcp", version, about)]
struct Args {
    /// Path to the Alloy project directory
    #[arg(long)]
    project: Option<String>,
}

#[tokio::main]
async fn main() {
    let args = Args::parse();

    let state = match args.project {
        Some(ref path) => Arc::new(state::ProjectState::with_project(path)),
        None => Arc::new(state::ProjectState::new()),
    };

    server::run_server(state).await;
}

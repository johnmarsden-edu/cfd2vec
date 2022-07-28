use crate::server::{start, Result};

mod capnp;
mod cfg;
mod server;

use clap::{Parser, Subcommand};

#[derive(Parser)]
#[clap(name = "cfg", author, version, about)]
struct Cli {
    #[clap(subcommand)]
    action: Action,
}

#[derive(Subcommand, Debug)]
enum Action {
    /// Start the server on the desired port
    #[clap(arg_required_else_help = true)]
    Serve {
        #[clap(value_parser)]
        port: Option<String>,
    },
}

#[tokio::main]
async fn main() -> Result<()> {
    let cli: Cli = Cli::parse();
    match cli.action {
        Action::Serve { .. } => start().await,
    }
}

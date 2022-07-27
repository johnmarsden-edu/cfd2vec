use crate::server::{start, Result};
use std::path::PathBuf;

mod cfg;
mod messages;
mod server;

use crate::messages::message::Message;
use clap::{Parser, Subcommand};
use schemars::schema_for;

#[derive(Parser)]
#[clap(name = "cfg", author, version, about)]
struct Cli {
    #[clap(subcommand)]
    action: Action,
}

#[derive(Subcommand, Debug)]
enum Action {
    /// Print the schema to stdout or to the target file
    #[clap(arg_required_else_help = true)]
    Schema {
        /// Target file for schema
        #[clap(value_parser)]
        target: Option<PathBuf>,
    },

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
        Action::Schema {
            target: Some(target),
        } => {
            std::fs::write(
                target.clone(),
                serde_json::to_string_pretty(&schema_for!(Message)).unwrap(),
            )
            .unwrap_or_else(|e| {
                panic!(
                    "Failed to write schema to target {:#?} because of error {:#?}",
                    target, e
                )
            });
            Ok(())
        }
        Action::Schema { target: None } => {
            println!(
                "{}",
                serde_json::to_string_pretty(&schema_for!(Message)).unwrap()
            );
            Ok(())
        }
        Action::Serve { .. } => start().await,
    }
}

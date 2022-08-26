use crate::server::start;

mod capnp;
mod cfg;
mod db;
mod server;

use anyhow::Result;
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
        #[clap(default_value_t = false)]
        collect_mode: bool,
        #[clap(value_parser)]
        port: Option<String>,
    },
}

#[tokio::main]
async fn main() -> Result<()> {
    log4rs::init_file("logging.yml", Default::default())?;
    let cli: Cli = Cli::parse();
    match cli.action {
        Action::Serve { collect_mode, .. } => start(collect_mode).await?,
    }
    Ok(())
}

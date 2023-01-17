use crate::server::start;
use std::path::Path;

mod capnp;
mod cfg;
mod server;

use anyhow::Result;
use clap::{Args, Parser, Subcommand};

#[derive(Parser)]
#[clap(name = "cfg", author, version, about)]
struct Cli {
    #[clap(subcommand)]
    action: Action,
}

#[derive(Subcommand, Debug)]
enum Action {
    /// Start the server on the desired port
    Serve(Serve),
}

#[derive(Args, Debug)]
struct Serve {
    /// The output directory for the processed programs
    output_dir: String,
    #[arg(long, short = 'c')]
    /// Whether to collect messages for fuzzing purposes
    collect: bool,
    #[arg(long, short, default_value_t = 9271)]
    /// What port to serve the server on
    port: u32,
}

#[tokio::main]
async fn main() -> Result<()> {
    log4rs::init_file("logging.yml", Default::default())?;
    let cli: Cli = Cli::parse();
    match cli.action {
        Action::Serve(Serve {
            output_dir,
            collect,
            port,
        }) => start(Path::new(&output_dir).to_path_buf(), collect, port).await?,
    }
    Ok(())
}

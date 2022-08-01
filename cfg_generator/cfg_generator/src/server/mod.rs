#![deny(missing_doc_code_examples)]

use crate::capnp::readers::message;
use crate::cfg::{CfgEdge, CfgNode, MethodProcessingError};
use bytes::{Buf, BytesMut};
use capnp::message::TypedReader;
use error_chain::error_chain;
use log::{debug, info};
use petgraph::dot::Dot;
use petgraph::stable_graph::StableDiGraph;
use std::fs::write;
use std::path::PathBuf;
use tokio::io::AsyncWriteExt;
use tokio::net::{TcpListener, TcpStream};
use tokio::task;
use tokio_stream::wrappers::TcpListenerStream;
use tokio_stream::StreamExt;
use tokio_util::codec::{Framed, LengthDelimitedCodec};

error_chain!(
    foreign_links {
        Io(std::io::Error);
        MessageProcessing(MessageProcessingError);
        MethodProcessing(crate::cfg::Error);
        CapnP(capnp::Error);
    }
);

pub async fn start() -> Result<()> {
    const TCP_CONNECTION: &str = "localhost:9271";
    let mut listener = TcpListenerStream::new(TcpListener::bind(TCP_CONNECTION).await?);
    eprintln!("Accepting connections on {:?}", TCP_CONNECTION);
    while let Some(Ok(stream)) = listener.next().await {
        task::spawn(async {
            if let Err(e) = connection_loop(stream).await {
                eprintln!("Error: {}", e);
            }
        });
    }
    Ok(())
}

async fn connection_loop(mut stream: TcpStream) -> Result<()> {
    let peer_addr = stream.peer_addr()?;
    println!("Connected from {}", peer_addr);
    let mut frames = Framed::new(&mut stream, LengthDelimitedCodec::new());
    while let Some(result) = frames.next().await {
        match result {
            Ok(decoded_item) => {
                println!("{:?}", decoded_item);
                task::spawn(async {
                    if let Err(e) = process_decoded_item(decoded_item) {
                        eprintln!("Error during processing of decoded item: {}", e);
                    }
                });
            }
            Err(e) => {
                eprintln!(
                    "Error occurred during connection with {}: {:#?}",
                    peer_addr, e
                );
            }
        }
    }
    stream.shutdown().await?;
    println!("Disconnected from {}", peer_addr);
    Ok(())
}

pub fn process_decoded_item(msg: BytesMut) -> Result<()> {
    debug!("Processing msg: {:?}", msg);
    let reader =
        capnp::serialize::read_message(msg.reader(), capnp::message::ReaderOptions::default())?;
    let message_reader = TypedReader::<_, crate::capnp::message_capnp::message::Owned>::new(reader);

    let message_root = message_reader.get()?;
    debug!("Extracted message reader from msg");
    match process_message(&message_root) {
        Ok(graphs) => {
            let graphs_dir = PathBuf::from("graphs");
            for (id, graph) in graphs {
                let program_id = message_root.get_program_id()?;
                let graph_dir = graphs_dir.join(program_id).join(id.to_string());
                let contents = format!("{:?}", Dot::new(&graph));
                write(graph_dir, contents)?;
            }
            Ok(())
        }
        Err(e) => {
            info!("Error during processing of unpacked message: {:#?}", e);
            Ok(())
        }
    }
}

#[derive(thiserror::Error, Debug)]
pub enum MessageProcessingError {
    #[error("Method processing error occurred {0}")]
    MethodProcessingError(MethodProcessingError),
}

impl From<MethodProcessingError> for MessageProcessingError {
    fn from(e: MethodProcessingError) -> Self {
        MessageProcessingError::MethodProcessingError(e)
    }
}

fn process_message<'a>(
    message: &message::Reader<'a>,
) -> Result<Vec<(u32, StableDiGraph<CfgNode<'a>, CfgEdge<'a>>)>> {
    let mut graphs = Vec::new();
    for node_id in message.get_methods()? {
        graphs.push((
            node_id,
            crate::cfg::process_method(message.get_nodes()?, node_id)?,
        ));
    }
    Ok(graphs)
}

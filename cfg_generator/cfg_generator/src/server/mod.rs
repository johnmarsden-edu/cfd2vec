#![deny(missing_doc_code_examples)]

use crate::cfg::{CfgEdge, CfgNode, MethodProcessingError};
use crate::messages::message::Message;
use bytes::{Bytes, BytesMut};
use error_chain::{bail, error_chain};
use futures::SinkExt;
use log::{debug, info, trace};
use petgraph::dot::Dot;
use petgraph::stable_graph::StableDiGraph;
use std::borrow::Borrow;
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
        Serde(serde_json::Error);
        Io(std::io::Error);
        MessageProcessing(MessageProcessingError);
        MethodProcessing(MethodProcessingError);
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
    frames.send(Bytes::from("hello\n".as_bytes())).await?;
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
    match serde_json::from_slice(&msg) {
        Ok(msg) => {
            debug!("Extracted message from msg: {:#?}", msg);
            match process_message(&msg) {
                Ok(graphs) => {
                    let graphs_dir = PathBuf::from("graphs");
                    for (id, graph) in graphs {
                        let graph_dir = graphs_dir.join(&msg.program_id).join(id.to_string());
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
        Err(e) => bail!(e),
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

fn process_message(message: &Message) -> Result<Vec<(u32, StableDiGraph<CfgNode, CfgEdge>)>> {
    let mut graphs = Vec::new();
    for node_id in &message.methods {
        graphs.push((
            *node_id,
            crate::cfg::process_method(message.nodes.borrow(), *node_id as usize)?,
        ));
    }
    Ok(graphs)
}

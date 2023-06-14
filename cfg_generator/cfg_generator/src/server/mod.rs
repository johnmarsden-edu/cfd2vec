#![deny(missing_doc_code_examples)]

use std::path::{Path, PathBuf};
use std::time;
use std::time::{Duration, Instant};

use bytes::{Buf, BytesMut};
use capnp::message::TypedReader;
use futures::stream::FuturesOrdered;
use itertools::Itertools;
use log::{debug, error, info, trace};
use once_cell::sync::Lazy;
use petgraph::stable_graph::StableDiGraph;
use petgraph_graph_tool::attr_types::GtString;
use petgraph_graph_tool::{GraphTool, GraphToolAttribute};
use stream_cancel::Valve;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::mpsc;
use tokio::sync::mpsc::Sender;
use tokio::task;
use tokio::task::JoinHandle;
use tokio_stream::wrappers::{ReceiverStream, TcpListenerStream};
use tokio_stream::StreamExt;
use tokio_util::codec::{Framed, LengthDelimitedCodec};

use crate::capnp::message_capnp::message::debug::WhichReader;
use crate::capnp::readers::message;
use crate::cfg::{Edge, Node};
use crate::server::error::{CfgServerError, ConnectionError, MessageProcessingError};
use crate::server::message_processing::{ProcessedGraph, ProcessedProgram};

pub mod error;
pub mod message_processing;

fn start_save_task(
    mut save_stream: ReceiverStream<ProcessedProgram>,
    output_dir: PathBuf,
) -> JoinHandle<Result<(), CfgServerError>> {
    let timeout = Duration::new(5, 0);
    let batch_size = 10000;

    task::spawn(async move {
        debug!("Starting save task");
        let mut timeout_start = Instant::now();
        let mut batch = Vec::new();
        debug!("Now waiting for programs");
        loop {
            match tokio::time::timeout(Duration::from_secs(10), save_stream.next()).await {
                Ok(Some(program)) => {
                    debug!(
                        "Adding {} from {} to batch",
                        program.program_id, program.program_group
                    );
                    batch.push(program);
                    let time_passed = Instant::now() - timeout_start;
                    if batch.len() >= batch_size || time_passed > timeout {
                        info!("Took {:#?} to fill up the batch", time_passed);
                        timeout_start = Instant::now();
                        store_batch(&output_dir, &mut batch).await;
                    }
                }
                Ok(None) => {
                    if !batch.is_empty() {
                        info!("Program is ending and am running the batch storage with whatever is present");
                        store_batch(&output_dir, &mut batch).await;
                    }
                    break;
                }
                _ => {
                    if !batch.is_empty() {
                        info!("Met the timeout and am running the batch storage with whatever is present");
                        store_batch(&output_dir, &mut batch).await;
                    }
                }
            }
        }

        Result::<(), CfgServerError>::Ok(())
    })
}

async fn store_batch(output_dir: &Path, batch: &mut Vec<ProcessedProgram>) {
    let batch = batch
        .drain(..)
        .filter(|p| !p.graphs.is_empty())
        .collect_vec();
    async move {
        if let Err(e) = store_programs(output_dir, batch).await {
            error!("An error occurred while storing the programs: {}", e);
        }
    }
    .await;
}

pub async fn start(
    output_dir: PathBuf,
    collect_mode: bool,
    port: u32,
) -> Result<(), CfgServerError> {
    // Create the connection port
    let tcp_connection: String = format!("::0:{}", port);
    let bound_listener = TcpListener::bind(tcp_connection).await?;
    let address = bound_listener.local_addr()?;
    info!("Accepting connections on {:?}", address);
    let listener = TcpListenerStream::new(bound_listener);

    // Create the save processing thread
    let (trigger, valve) = Valve::new();
    let (save_sender, save_receiver) = mpsc::channel::<ProcessedProgram>(100000);
    let save_stream = ReceiverStream::new(save_receiver);
    let db_task = start_save_task(save_stream, output_dir);

    let mut connections = FuturesOrdered::default();
    let mut incoming = valve.wrap(listener);
    let mut trigger = Some(trigger);
    ctrlc::set_handler(move || {
        if let Some(trigger) = trigger.take() {
            trigger.cancel();
        }
    })?;

    while let Some(Ok(stream)) = incoming.next().await {
        let sender = save_sender.clone();
        connections.push_back(task::spawn(async move {
            if let Err(e) = connection_loop(stream, collect_mode, sender).await {
                error!("Error during connection loop: {:?}", e);
            }
        }));
    }

    info!("Start disconnecting");
    while connections.next().await.is_some() {
        info!("Disconnected");
    }
    drop(save_sender);
    db_task.await.unwrap().unwrap();
    Ok(())
}

async fn connection_loop(
    mut stream: TcpStream,
    collect_mode: bool,
    db_sender: Sender<ProcessedProgram>,
) -> Result<(), ConnectionError> {
    let peer_addr = stream.peer_addr()?;
    info!("Connected from {}", peer_addr);
    let mut frames = Framed::new(&mut stream, LengthDelimitedCodec::new());
    let mut message_processors = FuturesOrdered::default();
    while let Some(result) = frames.next().await {
        match result {
            Ok(decoded_item) => {
                trace!("Decoded item: {:?}", decoded_item);
                let sender = db_sender.clone();
                message_processors.push_back(task::spawn(async move {
                    let decoded_item = decoded_item;
                    match process_decoded_item(decoded_item, collect_mode) {
                        Ok(program) => {
                            trace!("Starting to store the program");
                            sender.send(program).await?;
                        }
                        Err(e) => error!("Error during processing of decoded item: {}", e),
                    }
                    Result::<(), MessageProcessingError>::Ok(())
                }));
            }
            Err(e) => {
                info!(
                    "Error occurred during connection with {}: {:#?}",
                    peer_addr, e
                );
            }
        }
    }
    tokio::io::AsyncWriteExt::shutdown(&mut stream).await?;
    info!("Disconnected from {}", peer_addr);

    let mut count = 0;
    while message_processors.next().await.is_some() {
        if count % 1000 == 0 {
            trace!("{} messages processed from {}", count, peer_addr);
        }
        count += 1;
    }
    info!("Completed processing messages from {}", peer_addr);
    Ok(())
}

static FUZZ_DIR: Lazy<PathBuf> = Lazy::new(|| {
    let fuzz_dir = PathBuf::from("fuzzing_corpus");
    std::fs::create_dir_all(fuzz_dir.clone()).expect("Could not create the fuzzing directory");
    fuzz_dir
});

pub fn process_decoded_item(
    msg: BytesMut,
    collect_mode: bool,
) -> Result<ProcessedProgram, MessageProcessingError> {
    trace!("Processing msg: {:?}", msg);
    let cloned_msg = if collect_mode {
        trace!("Cloned message for collection");
        Some(msg.clone())
    } else {
        None
    };

    let reader = capnp::serialize::read_message(
        msg.reader(),
        *capnp::message::ReaderOptions::default().nesting_limit(512),
    )?;
    let message_reader = TypedReader::<_, message::Owned>::new(reader);

    let message_root = message_reader.get()?;
    let program_id = message_root.get_program_id()?;
    let program_group = message_root.get_program_group()?;

    trace!("program_id: {:#?}", program_id);
    let program_id = program_id.to_string();
    trace!("Extracted message reader from msg");

    trace!("program_group: {:#?}", program_group);
    let program_group = program_group.to_string();

    if collect_mode {
        trace!("Collecting message: {program_id}");
        let message_file = FUZZ_DIR.join(program_id.clone());
        std::fs::write(message_file, cloned_msg.unwrap())?;
    }

    let mut program = ProcessedProgram {
        program_id,
        program_group,
        graphs: Vec::new(),
    };

    match message_processing::process_message(&message_root) {
        Ok(graphs) => {
            for (id, graph) in graphs {
                let graph_id = if let Some(i) = id {
                    trace!("graph_id: {:#?}", i);
                    i
                } else if let Ok(timestamp) =
                    time::SystemTime::now().duration_since(time::SystemTime::UNIX_EPOCH)
                {
                    trace!("timestamp: {:#?}", timestamp.as_nanos());
                    format!("{}", timestamp.as_nanos())
                } else {
                    error!("Error with creating graph_id");
                    Err(MessageProcessingError::GraphCreation)?
                };

                trace!("graph_id: {:#?}", graph_id);
                program.graphs.push(process_graph(graph, graph_id)?);
            }
            Ok(program)
        }
        Err(e) => {
            error!("Error during method processing");
            match message_root.get_debug().which()? {
                WhichReader::Some(code) => Err(MessageProcessingError::MethodProcessingDebug {
                    program_id: program.program_id,
                    error: e,
                    code: code?.to_string(),
                }),
                WhichReader::None(_) => Err(MessageProcessingError::MethodProcessing {
                    program_id: program.program_id,
                    error: e,
                }),
            }
        }
    }
}

fn process_graph(
    graph: StableDiGraph<Node, Edge>,
    graph_id: String,
) -> Result<ProcessedGraph, MessageProcessingError> {
    trace!("Processing graph: {}", graph_id);
    let mut graph_tool = GraphTool::new(graph);
    graph_tool
        .add_edge_attribute::<GtString>(GraphToolAttribute::new(
            "transfer",
            Box::new(|edge| format!("{}", edge).into()),
        ))
        .add_node_attribute::<GtString>(GraphToolAttribute::new(
            "code",
            Box::new(|node| format!("{}", node.node_type).into()),
        ));

    Ok(ProcessedGraph {
        graph_id,
        graph_contents: graph_tool.to_bytes()?,
    })
}

async fn store_programs(
    output_dir: &Path,
    programs: Vec<ProcessedProgram>,
) -> Result<(), MessageProcessingError> {
    debug!("Number of programs to be stored: {}", programs.len());
    for program in programs {
        debug!(
            "Storing {} from program group {}",
            program.program_id, program.program_group
        );
        let program_dir = output_dir
            .join(program.program_group)
            .join(program.program_id);
        tokio::fs::create_dir_all(program_dir.clone())
            .await
            .expect("Hmm");
        debug!("Number of graphs: {}", program.graphs.len());
        for graph in program.graphs {
            tokio::fs::write(program_dir.join(graph.graph_id), graph.graph_contents)
                .await
                .expect("Bro whats up");
        }
    }
    Ok(())
}

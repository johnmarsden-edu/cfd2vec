#![deny(missing_doc_code_examples)]

use std::collections::HashMap;
use std::path::PathBuf;
use std::time;
use std::time::{Duration, Instant};

use bytes::{Buf, BytesMut};
use capnp::message::TypedReader;
use deadpool_postgres::{Manager, ManagerConfig, Pool, RecyclingMethod};
use dotenvy::dotenv;
use futures::stream::FuturesOrdered;
use itertools::Itertools;
use log::{error, info, trace};
use once_cell::sync::Lazy;
use petgraph::dot::Dot;
use petgraph::prelude::EdgeRef;
use petgraph::stable_graph::{NodeIndex, StableDiGraph};
use petgraph::visit::{IntoEdgeReferences, IntoNodeReferences};
use stream_cancel::Valve;
use tokio::net::{TcpListener, TcpStream};
use tokio::sync::mpsc;
use tokio::sync::mpsc::Sender;
use tokio::task;
use tokio_postgres::types::{ToSql, Type};
use tokio_postgres::{Client, NoTls};
use tokio_stream::wrappers::{ReceiverStream, TcpListenerStream};
use tokio_stream::StreamExt;
use tokio_util::codec::{Framed, LengthDelimitedCodec};

use crate::capnp::message_capnp::message::debug::WhichReader;
use crate::capnp::readers::message;
use crate::cfg::cfg_node::{ControlType, Direction, NodeType};
use crate::cfg::{Edge, Node};
use crate::db::{cornucopia, store_values};
use crate::server::error::{CfgServerError, ConnectionError, MessageProcessingError};
use crate::server::message_processing::{
    ProcessedEdge, ProcessedGraph, ProcessedNode, ProcessedProgram,
};

pub mod error;
pub mod message_processing;

pub async fn start(collect_mode: bool) -> Result<(), CfgServerError> {
    dotenv()?;
    let cfg = crate::db::Config::from_env()?;

    let manager_cfg = ManagerConfig {
        recycling_method: RecyclingMethod::Fast,
    };

    let manager = Manager::from_config(cfg.pg, NoTls, manager_cfg);
    let pool = Pool::builder(manager).max_size(70usize).build()?;

    const TCP_CONNECTION: &str = "::0:9271";
    let bound_listener = TcpListener::bind(TCP_CONNECTION).await?;
    let address = bound_listener.local_addr()?;
    info!("Accepting connections on {:?}", address);
    let listener = TcpListenerStream::new(bound_listener);

    let (trigger, valve) = Valve::new();
    let (db_sender, db_receiver) = mpsc::channel::<ProcessedProgram>(4000);
    let mut db_stream = ReceiverStream::new(db_receiver);
    let timeout = Duration::new(5, 0);
    let batch_size = 10000;
    let db_task = task::spawn(async move {
        let mut timeout_start = Instant::now();
        let mut batch = Vec::new();
        let mut batches_processing = FuturesOrdered::default();
        while let Some(program) = db_stream.next().await {
            batch.push(program);
            let time_passed = Instant::now() - timeout_start;
            if batch.len() >= batch_size || time_passed > timeout {
                info!("Took {:#?} to fill up the batch", time_passed);
                timeout_start = Instant::now();
                let batch = batch
                    .drain(..)
                    .filter(|p| !p.graphs.is_empty())
                    .collect_vec();
                let mut conn = pool.get().await?;
                batches_processing.push_back(task::spawn(async move {
                    if let Err(e) = store_programs(&mut conn, batch).await {
                        error!("An error occurred while storing the program: {}", e);
                    }
                }));
            }
        }

        while batches_processing.next().await.is_some() {}
        Result::<(), CfgServerError>::Ok(())
    });

    let mut connections = FuturesOrdered::default();
    let mut incoming = valve.wrap(listener);
    let mut trigger = Some(trigger);
    ctrlc::set_handler(move || {
        if let Some(trigger) = trigger.take() {
            trigger.cancel();
        }
    })?;

    while let Some(Ok(stream)) = incoming.next().await {
        let sender = db_sender.clone();
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
    drop(db_sender);
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

static _GRAPH_DIR: Lazy<PathBuf> = Lazy::new(|| {
    let graph_dir = PathBuf::from("graphs");
    std::fs::create_dir_all(graph_dir.clone()).expect("Could not create the graph directory");
    graph_dir
});
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

    let reader =
        capnp::serialize::read_message(msg.reader(), capnp::message::ReaderOptions::default())?;
    let message_reader = TypedReader::<_, message::Owned>::new(reader);

    let message_root = message_reader.get()?;
    let program_id = message_root.get_program_id()?;

    trace!("{:#?}", program_id);
    let program_id = program_id.to_string();
    trace!("Extracted message reader from msg");

    if collect_mode {
        trace!("Collecting message: {program_id}");
        let message_file = FUZZ_DIR.join(program_id.clone());
        std::fs::write(message_file, cloned_msg.unwrap())?;
    }

    let mut program = ProcessedProgram {
        program_id,
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
                program
                    .graphs
                    .push(process_graph(graph, graph_id, &program.program_id)?);
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
    program_id: &String,
) -> Result<ProcessedGraph, MessageProcessingError> {
    Ok(ProcessedGraph {
        graph_id: graph_id.clone(),
        nodes: process_nodes(&graph, &graph_id, program_id)?,
        edges: process_edges(&graph, &graph_id, program_id)?,
    })
}

fn process_nodes(
    graph: &StableDiGraph<Node, Edge>,
    graph_id: &String,
    program_id: &String,
) -> Result<Vec<ProcessedNode>, MessageProcessingError> {
    let mut nodes = Vec::new();
    for (node_index, node) in graph.node_references() {
        let (node_type, contents) = match &node.node_type {
            NodeType::Source { name } => (
                cornucopia::types::public::NodeType::Source,
                name.map(|name| name.to_string()),
            ),
            NodeType::Sink { name } => (
                cornucopia::types::public::NodeType::Sink,
                name.map(|name| name.to_string()),
            ),
            NodeType::Statement { statement } => (
                cornucopia::types::public::NodeType::Statement,
                Some(statement.to_string()),
            ),
            NodeType::ControlNode {
                control_type,
                contents,
            } => (
                cornucopia::types::public::NodeType::Control,
                match control_type {
                    ControlType::Return(term) => contents.map(|content| format!("{} {}", term, content)),
                    ControlType::Break(term)
                    | ControlType::_Yield(term)
                    | ControlType::Continue(term) => {
                        node.label.map(|label| format!("{} {}", term, label))
                    }
                },
            ),
            NodeType::Decision { decision } => (
                cornucopia::types::public::NodeType::Decision,
                Some(decision.to_string()),
            ),
            NodeType::Exception { term, statement } => (
                cornucopia::types::public::NodeType::Exception,
                Some(format!("{} {}", term, statement)),
            ),
            NodeType::Label => {
                error!(
                    "A label node labeled {:?} remained in program {} graph {}: \n{}",
                    node.label,
                    program_id,
                    graph_id,
                    Dot::new(graph)
                );
                Err(MessageProcessingError::GraphCreation)?
            }
        };
        nodes.push(ProcessedNode {
            node_index,
            label: if let NodeType::ControlNode { .. } = node.node_type {
                None
            } else {
                node.label.map(|label| label.to_string())
            },
            node_type,
            contents,
        });
    }
    Ok(nodes)
}

fn process_edges(
    graph: &StableDiGraph<Node, Edge>,
    graph_id: &String,
    program_id: &String,
) -> Result<Vec<ProcessedEdge>, MessageProcessingError> {
    let mut edges = Vec::new();
    for edge_ref in graph.edge_references() {
        let (edge_type, direction, exception) = match edge_ref.weight() {
            Edge::Statement => (cornucopia::types::public::EdgeType::Statement, None, None),
            Edge::Decision(direction) => (
                cornucopia::types::public::EdgeType::Decision,
                Some(match direction {
                    Direction::True => true,
                    Direction::False => false,
                }),
                None,
            ),
            Edge::Exception { exception } => (
                cornucopia::types::public::EdgeType::Exception,
                None,
                Some(exception.to_string()),
            ),
            Edge::Label(_) => {
                error!(
                    "A label edge remained in program {} graph {}",
                    program_id, graph_id
                );
                Err(MessageProcessingError::GraphCreation)?
            }
        };

        edges.push(ProcessedEdge {
            source: edge_ref.source(),
            target: edge_ref.target(),
            edge_type,
            direction,
            exception,
        })
    }

    Ok(edges)
}

async fn store_programs(
    db_conn: &mut Client,
    programs: Vec<ProcessedProgram>,
) -> Result<(), MessageProcessingError> {
    trace!("Start transaction");
    let transaction = db_conn.transaction().await?;

    trace!("Create temporary tables");
    transaction
        .execute(
            &"CREATE TEMP TABLE programs_temp (LIKE programs INCLUDING ALL) ON COMMIT DROP"
                .to_string(),
            &[],
        )
        .await?;
    transaction
        .execute(
            &"CREATE TEMP TABLE graphs_temp (LIKE graphs INCLUDING ALL) ON COMMIT DROP".to_string(),
            &[],
        )
        .await?;
    transaction
        .execute(
            &"CREATE TEMP TABLE edges_temp (LIKE edges INCLUDING ALL) ON COMMIT DROP".to_string(),
            &[],
        )
        .await?;
    transaction
        .execute(
            &"CREATE TEMP TABLE nodes_temp (LIKE nodes INCLUDING ALL) ON COMMIT DROP".to_string(),
            &[],
        )
        .await?;

    let program_insert_types = &[Type::VARCHAR];
    let program_data: Vec<Vec<&(dyn ToSql + Sync)>> = programs
        .iter()
        .map(|program| vec![&program.program_id as &(dyn ToSql + Sync)])
        .collect();
    let program_ids = store_values(
        &transaction,
        "programs".to_string(),
        "program_id".to_string(),
        program_insert_types,
        program_data
            .iter()
            .map(|e| e.as_slice())
            .collect_vec()
            .as_slice(),
    )
    .await?;
    let graph_insert_types = &[Type::VARCHAR, Type::INT4];
    let graph_data_store: Vec<(&String, i32)> = programs
        .iter()
        .zip(program_ids)
        .flat_map(|(program, program_id)| {
            let program_id = &program_id;
            program
                .graphs
                .iter()
                .map(|graph| (&graph.graph_id, *program_id))
                .collect::<Vec<_>>()
        })
        .collect();

    let graph_data: Vec<[&(dyn ToSql + Sync); 2]> = graph_data_store
        .iter()
        .map(|(graph_id, program_id)| [*graph_id as &(dyn ToSql + Sync), program_id])
        .collect();

    trace!(
        "Inserting graph info - Number of graphs: {}",
        graph_data.len()
    );
    let graph_ids = store_values(
        &transaction,
        "graphs".to_string(),
        "graph_id, program_id".to_string(),
        graph_insert_types,
        graph_data
            .iter()
            .map(|e| e.as_slice())
            .collect_vec()
            .as_slice(),
    )
    .await?;

    let get_enum_oid_stmt = transaction
        .prepare(
            "SELECT pg_type.typname, pg_type.oid, string_agg(pg_enum.enumlabel, '|')
                    FROM pg_enum
                    JOIN pg_type
                      ON (pg_enum.enumtypid = pg_type.oid)
                    WHERE typname = $1
                    GROUP BY pg_type.typname, pg_type.oid",
        )
        .await?;

    trace!("Getting node_type_info");
    let node_type_info = transaction
        .query_one(&get_enum_oid_stmt, &[&"node_type"])
        .await?;
    let variants = node_type_info
        .get::<usize, String>(2)
        .split('|')
        .map(|s| s.to_string())
        .collect_vec();
    let node_type = Type::new(
        node_type_info.get(0),
        node_type_info.get(1),
        postgres_types::Kind::Enum(variants),
        "public".to_string(),
    );
    let node_types = &[Type::VARCHAR, node_type, Type::VARCHAR, Type::INT4];
    let node_data: Vec<Vec<&(dyn ToSql + Sync)>> = programs
        .iter()
        .flat_map(|program| program.graphs.iter())
        .zip(graph_ids.iter())
        .flat_map(|(graph, graph_id)| {
            graph.nodes.iter().map(|node| {
                vec![
                    &node.label as &(dyn ToSql + Sync),
                    &node.node_type,
                    &node.contents,
                    graph_id,
                ]
            })
        })
        .collect();

    trace!(
        "Inserting nodes for graph - Number of nodes: {}",
        node_data.len()
    );
    let node_ids = store_values(
        &transaction,
        "nodes".to_string(),
        "label, node_type, contents, graph_id".to_string(),
        node_types,
        node_data
            .iter()
            .map(|e| e.as_slice())
            .collect_vec()
            .as_slice(),
    )
    .await?;

    let node_ids: HashMap<(NodeIndex, i32), i32> = programs
        .iter()
        .flat_map(|program| program.graphs.iter())
        .zip(graph_ids.iter())
        .flat_map(|(graph, graph_id)| graph.nodes.iter().map(|node| (node, *graph_id)))
        .zip(node_ids)
        .map(|((node, graph_id), id)| ((node.node_index, graph_id), id))
        .collect();

    trace!("Getting edge_type_info");
    let edge_type_info = transaction
        .query_one(&get_enum_oid_stmt, &[&"edge_type"])
        .await?;
    let variants = edge_type_info
        .get::<usize, String>(2)
        .split('|')
        .map(|s| s.to_string())
        .collect_vec();
    let edge_type = Type::new(
        edge_type_info.get(0),
        edge_type_info.get(1),
        postgres_types::Kind::Enum(variants),
        "public".to_string(),
    );
    let edge_types = &[
        Type::INT4,
        Type::INT4,
        Type::INT4,
        edge_type,
        Type::BOOL,
        Type::VARCHAR,
    ];
    let edge_data: Vec<Vec<&(dyn ToSql + Sync)>> = programs
        .iter()
        .flat_map(|program| program.graphs.iter())
        .zip(graph_ids.iter())
        .flat_map(|(graph, graph_id)| {
            graph.edges.iter().map(|edge| {
                let source = node_ids.get(&(edge.source, *graph_id)).unwrap();
                let target = node_ids.get(&(edge.target, *graph_id)).unwrap();

                vec![
                    graph_id as &(dyn ToSql + Sync),
                    source,
                    target,
                    &edge.edge_type,
                    &edge.direction,
                    &edge.exception,
                ]
            })
        })
        .collect();

    trace!(
        "Inserting edges for graph - Number of edges: {}",
        edge_data.len()
    );
    store_values(
        &transaction,
        "edges".to_string(),
        "graph_id, source, target, edge_type, direction, exception".to_string(),
        edge_types,
        edge_data
            .iter()
            .map(|e| e.as_slice())
            .collect_vec()
            .as_slice(),
    )
    .await?;

    transaction.commit().await?;
    Ok(())
}

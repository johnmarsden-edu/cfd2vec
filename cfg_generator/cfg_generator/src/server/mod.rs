#![deny(missing_doc_code_examples)]

use crate::capnp::message_capnp::message::debug::WhichReader;
use crate::capnp::readers::message;
use crate::cfg::cfg_node::{Direction, NodeType};
use crate::cfg::{Edge, MethodProcessingError, Node};
use crate::db::cornucopia;
use bytes::{Buf, BytesMut};
use capnp::message::TypedReader;
use deadpool_postgres::{Manager, ManagerConfig, Pool, RecyclingMethod};
use dotenvy::dotenv;
use futures::stream::FuturesOrdered;
use futures::{pin_mut, StreamExt};
use itertools::Itertools;
use log::{debug, error, info, trace};
use once_cell::sync::Lazy;
use petgraph::dot::Dot;
use petgraph::prelude::EdgeRef;
use petgraph::stable_graph::{NodeIndex, StableDiGraph};
use petgraph::visit::{IntoEdgeReferences, IntoNodeReferences};
use std::borrow::Borrow;
use std::collections::HashMap;
use std::path::PathBuf;
use std::time;
use tokio::net::{TcpListener, TcpStream};
use tokio::task;
use tokio_postgres::binary_copy::BinaryCopyInWriter;
use tokio_postgres::types::{ToSql, Type};
use tokio_postgres::{Client, NoTls, Transaction};
use tokio_stream::wrappers::TcpListenerStream;
use tokio_util::codec::{Framed, LengthDelimitedCodec};

#[derive(thiserror::Error, Debug)]
pub enum CfgServerError {
    #[error("There was an error setting up the TCP socket: {error}")]
    TcpServer {
        #[from]
        error: std::io::Error,
    },
    #[error("There was an error running dotenv: {error}")]
    DotEnv {
        #[from]
        error: dotenvy::Error,
    },
    #[error("There was an error configuring the database connection: {error}")]
    Config {
        #[from]
        error: config::ConfigError,
    },
    #[error("There was an error creating the database connection pool: {error}")]
    CreatePool {
        #[from]
        error: deadpool_postgres::CreatePoolError,
    },
    #[error("There was an error building the database pool: {error}")]
    BuildPool {
        #[from]
        error: deadpool_postgres::BuildError,
    },
}

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
    info!(
        "Accepting connections on {:?}",
        bound_listener.local_addr()?
    );

    let mut connections = FuturesOrdered::default();
    let mut listener = TcpListenerStream::new(bound_listener);
    while let Some(Ok(stream)) = listener.next().await {
        let pool = pool.clone();
        connections.push_back(task::spawn(async move {
            if let Err(e) = connection_loop(stream, collect_mode, pool).await {
                error!("Error during connection loop: {:?}", e);
            }
        }));
    }

    while connections.next().await.is_some() {}
    Ok(())
}

#[derive(thiserror::Error, Debug)]
pub enum ConnectionError {
    #[error("There was an error during the TCP connection: {error}")]
    TcpConnectionError {
        #[from]
        error: std::io::Error,
    },
    #[error("There was an error getting a database connection: {error}")]
    DatabasePoolConnectError {
        #[from]
        error: deadpool_postgres::PoolError,
    },
}

async fn connection_loop(
    mut stream: TcpStream,
    collect_mode: bool,
    pool: Pool,
) -> Result<(), ConnectionError> {
    let peer_addr = stream.peer_addr()?;
    info!("Connected from {}", peer_addr);
    let mut frames = Framed::new(&mut stream, LengthDelimitedCodec::new());
    let mut message_processors = FuturesOrdered::default();
    while let Some(result) = frames.next().await {
        match result {
            Ok(decoded_item) => {
                trace!("Decoded item: {:?}", decoded_item);
                let pool = pool.clone();
                message_processors.push_back(task::spawn(async move {
                    let decoded_item = decoded_item;
                    match process_decoded_item(decoded_item, collect_mode) {
                        Ok(program) => {
                            let mut db_conn = pool.get().await?;
                            store_program(&mut db_conn, program).await?
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

#[derive(thiserror::Error, Debug)]
pub enum MessageProcessingError {
    #[error("Error processing program {program_id} message: {error}\n\nStarted with the following code: {code}")]
    MethodProcessingDebug {
        program_id: String,
        error: MethodProcessingError,
        code: String,
    },
    #[error("Error processing program {program_id} message: {error}")]
    MethodProcessing {
        program_id: String,
        error: MethodProcessingError,
    },
    #[error("Error during cap'n proto reading: {error}")]
    CapnProto {
        #[from]
        error: capnp::Error,
    },
    #[error("Error during cap'n proto reading not in schema: {error}")]
    CapnProtoNotInSchema {
        #[from]
        error: capnp::NotInSchema,
    },
    #[error("Error while performing IO: {error}")]
    Io {
        #[from]
        error: std::io::Error,
    },
    #[error("Error while running database queries: {error}")]
    Database {
        #[from]
        error: tokio_postgres::Error,
    },
    #[error("Error while trying to create a file for a graph in the message")]
    GraphCreation,
    #[error("There was an error getting a database connection from the pool: {error}")]
    Pool {
        #[from]
        error: deadpool_postgres::PoolError,
    },
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

pub struct ProcessedNode {
    pub node_index: NodeIndex,
    pub label: Option<String>,
    pub node_type: cornucopia::types::public::NodeType,
    pub contents: Option<String>,
}

pub struct ProcessedEdge {
    pub source: NodeIndex,
    pub target: NodeIndex,
    pub edge_type: cornucopia::types::public::EdgeType,
    pub direction: Option<bool>,
    pub exception: Option<String>,
}

pub struct ProcessedGraph {
    pub graph_id: String,
    pub nodes: Vec<ProcessedNode>,
    pub edges: Vec<ProcessedEdge>,
}

pub struct ProcessedProgram {
    pub program_id: String,
    pub graphs: Vec<ProcessedGraph>,
}

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

    match process_message(&message_root) {
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
                // debug!("graphs_dir: {:#?}", program_dir);
                // let graph_file = program_dir.join(format!("{}.dot", graph_id));
                // debug!("graph_file: {:#?}", graph_file);
                // let contents = format!("{}", Dot::new(&graph));
                // write(graph_file, contents)?;
                program
                    .graphs
                    .push(process_graph(graph, graph_id, &program.program_id)?);
            }
            Ok(program)
        }
        Err(e) => {
            debug!("Error during method processing");
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
            NodeType::ControlNode(_) => (cornucopia::types::public::NodeType::Control, None),
            NodeType::Decision { decision } => (
                cornucopia::types::public::NodeType::Decision,
                Some(decision.to_string()),
            ),
            NodeType::Exception { statement } => (
                cornucopia::types::public::NodeType::Exception,
                Some(statement.to_string()),
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
            label: node.label.map(|label| label.to_string()),
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

async fn store_values<'a>(
    transaction: &Transaction<'a>,
    table: String,
    columns: String,
    types: &'a [Type],
    values: &'a [&'a [&'a (dyn ToSql + Sync)]],
) -> Result<Vec<i32>, MessageProcessingError> {
    let copy_sink = transaction
        .copy_in(&format!(
            "COPY {table}_temp ({columns}) FROM STDIN (FORMAT binary)"
        ))
        .await?;

    let writer = BinaryCopyInWriter::new(copy_sink, types);
    pin_mut!(writer);
    for value in values {
        writer.as_mut().write(value).await?;
    }
    writer.finish().await?;

    let ids: Vec<i32> = transaction
        .query(
            &format!(
                "INSERT INTO {table} ({columns}) SELECT {columns} FROM {table}_temp RETURNING id"
            ),
            &[],
        )
        .await?
        .into_iter()
        .map(|row| row.get(0))
        .collect();
    Ok(ids)
}

async fn store_program(
    db_conn: &mut Client,
    program: ProcessedProgram,
) -> Result<(), MessageProcessingError> {
    let program_id = cornucopia::queries::program::insert_program()
        .bind(db_conn, &program.program_id.borrow())
        .one()
        .await
        .map(|program| program.id)?;

    let transaction = db_conn.transaction().await?;

    transaction
        .execute(
            &"CREATE TEMP TABLE graphs_temp (LIKE graphs INCLUDING ALL) ON COMMIT DROP;
                CREATE TEMP TABLE edges_temp (LIKE edges INCLUDING ALL) ON COMMIT DROP;
                CREATE TEMP TABLE nodes_temp (LIKE nodes INCLUDING ALL) ON COMMIT DROP;"
                .to_string(),
            &[],
        )
        .await?;

    let graph_insert_types = &[Type::VARCHAR, Type::INT4];
    let graph_data: Vec<Vec<&(dyn ToSql + Sync)>> = program
        .graphs
        .iter()
        .map(|graph| vec![&graph.graph_id as &(dyn ToSql + Sync), &program_id])
        .collect();
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

    let node_types = &[Type::VARCHAR, Type::ANYENUM, Type::VARCHAR, Type::INT4];
    let node_data: Vec<Vec<&(dyn ToSql + Sync)>> = program
        .graphs
        .iter()
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

    let node_ids: HashMap<NodeIndex, i32> = program
        .graphs
        .iter()
        .flat_map(|graph| graph.nodes.iter())
        .zip(node_ids)
        .map(|(node, id)| (node.node_index, id))
        .collect();

    let edge_types = &[
        Type::INT4,
        Type::INT4,
        Type::INT4,
        Type::ANYENUM,
        Type::BOOL,
        Type::VARCHAR,
    ];
    let edge_data: Vec<Vec<&(dyn ToSql + Sync)>> = program
        .graphs
        .iter()
        .zip(graph_ids.iter())
        .flat_map(|(graph, graph_id)| {
            graph.edges.iter().map(|edge| {
                let source = node_ids.get(&edge.source).unwrap();
                let target = node_ids.get(&edge.target).unwrap();

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

    let _ = store_values(
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

type MessageProcessingResult<'a> =
    Result<Vec<(Option<String>, StableDiGraph<Node<'a>, Edge<'a>>)>, MethodProcessingError>;

fn process_message<'a>(message: &message::Reader<'a>) -> MessageProcessingResult<'a> {
    let mut graphs = Vec::new();
    for node in message.get_methods()? {
        match crate::cfg::process_method(node) {
            Ok(processed) => graphs.push(processed),
            Err(e) => Err(e)?,
        }
    }
    Ok(graphs)
}

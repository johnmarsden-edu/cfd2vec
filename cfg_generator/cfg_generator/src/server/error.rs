use log::error;
use tokio::sync::mpsc;

use crate::cfg::MethodProcessingError;
use crate::server::message_processing::ProcessedProgram;

#[derive(thiserror::Error, Debug)]
pub enum CfgServerError {
    #[error("There was an error setting up the TCP socket: {error}")]
    TcpServer {
        #[from]
        error: std::io::Error,
    },
    #[error("There was an error registering the termination listener")]
    CtrlC {
        #[from]
        error: ctrlc::Error,
    },
}

#[derive(thiserror::Error, Debug)]
pub enum ConnectionError {
    #[error("There was an error during the TCP connection: {error}")]
    TcpConnectionError {
        #[from]
        error: std::io::Error,
    },
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
    #[error("Error while trying to create a file for a graph in the message")]
    GraphCreation,
    #[error("Error while sending a processed program to the DB storage worker: {error}")]
    Send {
        #[from]
        error: mpsc::error::SendError<ProcessedProgram>,
    },
}

extern crate core;

mod codec;
mod messages;

use std::borrow::Borrow;
use std::env::args;
use std::error::Error;
use std::fs::remove_file;
use std::io;
use std::net::Shutdown;
use std::pin::Pin;
use std::task::{Context, Poll};
use std::time::Duration;

use async_std::net::TcpListener;
use async_std::prelude::*;
use async_std::task;

use crate::codec::DecoderItem;
use crate::messages::message::Message;
use async_listen::{backpressure, error_hint, ByteStream, ListenExt};
use async_std::io::Read;
use tokio::io::{AsyncRead as TokioAsyncRead, ReadBuf};
use tokio_util::codec::FramedRead;

use rmp_serde;

fn main() -> Result<(), Box<dyn Error>> {
    let (_, rx) = backpressure::new(10);
    #[cfg(unix)]
    {
        use async_std::os::unix::net::UnixListener;
        const SOCKET_FILE: &str = "/tmp/cfg.sock";

        if args().any(|x| x == "--unix") {
            remove_file(SOCKET_FILE).ok();
            return task::block_on(async {
                let listener = UnixListener::bind(SOCKET_FILE).await?;
                eprintln!("Accepting connections on {:?}", SOCKET_FILE);
                let mut incoming = listener
                    .incoming()
                    .log_warnings(log_accept_error)
                    .handle_errors(Duration::from_millis(500))
                    .backpressure_wrapper(rx);
                while let Some(stream) = incoming.next().await {
                    task::spawn(connection_loop(stream));
                }
                Ok(())
            });
        }
    }
    task::block_on(async {
        const TCP_CONNECTION: &str = "localhost:9271";
        let listener = TcpListener::bind(TCP_CONNECTION).await?;
        eprintln!("Accepting connections on {:?}", TCP_CONNECTION);
        let mut incoming = listener
            .incoming()
            .log_warnings(log_accept_error)
            .handle_errors(Duration::from_millis(500))
            .backpressure_wrapper(rx);
        while let Some(stream) = incoming.next().await {
            task::spawn(async {
                if let Err(e) = connection_loop(stream).await {
                    eprintln!("Error: {}", e);
                }
            });
        }
        Ok(())
    })
}

struct BsReader<'a>(&'a ByteStream);

impl<'a> TokioAsyncRead for BsReader<'a> {
    fn poll_read(
        mut self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<io::Result<()>> {
        Pin::new(&mut self.0)
            .poll_read(cx, buf.initialized_mut())
            .map(|f| match f {
                Ok(_) => Ok(()),
                Err(e) => Err(e),
            })
    }
}

async fn connection_loop(mut stream: ByteStream) -> Result<(), io::Error> {
    println!("Connected from {}", stream.peer_addr()?);
    let mut frames = FramedRead::new(BsReader(&stream), codec::SizedDataCodec::new());
    while let Some(result) = frames.next().await {
        match result {
            Ok(decoded_item) => {
                task::spawn(async {
                    if let Err(e) = process_decoded_item(decoded_item).await {
                        eprintln!("Error during processing of decoded item: {}", e);
                    }
                });
            }
            Err(e) => {
                eprintln!(
                    "Error occurred during connection with {}: {:#?}",
                    stream.peer_addr()?,
                    e
                );
            }
        }
    }
    stream
        .shutdown(Shutdown::Both)
        .expect("TODO: panic message");
    println!("Disconnected from {}", stream.peer_addr()?);
    Ok(())
}

async fn process_decoded_item(decoded_msg: DecoderItem) -> Result<(), rmp_serde::decode::Error> {
    match rmp_serde::from_read::<&[u8], Message>(decoded_msg.bytes.borrow()) {
        Ok(msg) => match process_message(msg).await {
            Ok(_) => Ok(()),
            Err(e) => {
                eprintln!("Error during processing of unpacked message: {:#?}", e);
                Ok(())
            }
        },
        Err(e) => Err(e),
    }
}

#[derive(Debug)]
enum MessageProcessingError {}

async fn process_message(message: Message) -> Result<(), MessageProcessingError> {}

fn log_accept_error(e: &io::Error) {
    eprintln!("Accept error: {}. Sleeping 0.5s. {}", e, error_hint(e));
}

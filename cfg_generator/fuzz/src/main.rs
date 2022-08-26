#![feature(is_some_with)]
#[macro_use]
extern crate afl;
extern crate cfg_generator;

use bytes::BytesMut;
use log::debug;
use log::trace;
use std::borrow::Borrow;
use std::io;

fn main() -> io::Result<()> {
    // simple_logging::log_to_file("fuzzing.log", log::LevelFilter::Debug)?;
    //
    // fuzz!(|message: &[u8]| {
    //     trace!("Fuzzing using following message: {:?}", message);
    //
    //     if data.len() <= u32::MAX as usize {
    //         let data: &[u8] = data.borrow();
    //         let bites: BytesMut = BytesMut::from(data);
    //         match cfg_generator::server::process_decoded_item(bites) {
    //             Ok(()) => {
    //                 trace!("Successfully fuzzed using the above message");
    //             }
    //             Err(e) => {
    //                 debug!("Error {:?} during fuzzing of message: {:#?}", e, message);
    //             }
    //         }
    //     }
    // });

    Ok(())
}

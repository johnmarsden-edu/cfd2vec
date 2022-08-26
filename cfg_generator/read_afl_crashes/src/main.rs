#![feature(is_some_with)]
extern crate afl;
extern crate cfg_generator;

use arbitrary::Unstructured;
use cfg_generator::capnp::readers::message::Reader;
use error_chain::error_chain;
use std::borrow::Borrow;
use std::fs::read;
use std::{env, fs};

error_chain! {
    foreign_links {
        Io(std::io::Error);
        Arbitrary(arbitrary::Error);
    }
}

fn main() -> Result<()> {
    // let current_dir = env::current_dir()?;
    // let input_dir = current_dir
    //     .join("second_fuzz_outputs")
    //     .join("default")
    //     .join("crashes");
    // for entry in fs::read_dir(input_dir)?
    //     .filter(|e| e.is_ok_and(|d| !d.file_name().eq_ignore_ascii_case("README.txt")))
    // {
    //     let entry = entry?;
    //
    //     let raw_data = &read(entry.path())?;
    //     let mut data = Unstructured::new(raw_data);
    //
    //     let message: Message = data.arbitrary()?;
    //
    //     println!("{:#?}", message);
    //
    //     if !message.methods.is_empty() && !message.nodes.is_empty() {
    //         match cfg_generator::cfg::process_method(
    //             message.nodes.borrow(),
    //             *message.methods.first().unwrap() as usize,
    //         ) {
    //             Ok(_) | Err(_) => {}
    //         };
    //     }
    // }

    Ok(())
}

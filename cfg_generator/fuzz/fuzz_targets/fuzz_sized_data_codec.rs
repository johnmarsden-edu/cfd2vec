#![no_main]
#[macro_use]
extern crate libfuzzer_sys;
extern crate cfg_generator;
extern crate rand;
use bytes::BytesMut;
use cfg_generator::codec::*;
use rand::rngs::OsRng;
use rand::seq::index::sample;
use rand::RngCore;
use tokio_util::codec::Decoder;

fuzz_target!(|data: &[u8]| {
    let mut decoder = SizedDataCodec::new();
    let mut buf = BytesMut::with_capacity(0);
    let length_bytes = data.len().to_be_bytes();
    let buf_bytes: Vec<u8> = length_bytes.iter().chain(data.iter()).copied().collect();
    let mut indices = sample(
        &mut OsRng,
        buf_bytes.len(),
        (OsRng.next_u32() as usize) % buf_bytes.len(),
    )
    .into_vec();
    indices.sort();
    if indices.is_empty() || indices[0] != 0 {
        indices.insert(0, 0);
    }

    if *indices.last().unwrap() != buf_bytes.len() {
        indices.push(buf_bytes.len());
    }

    let mut found_length = false;
    for pair in indices.windows(2) {
        buf.extend_from_slice(&buf_bytes[pair[0]..pair[1]]);
        if !found_length && pair[1] >= 8 {
            found_length = true;
            match decoder.decode(&mut buf) {
                Ok(opt) => {
                    if opt.is_some() {
                        panic!(
                            "Decode should just read the length. decoder: {:#?}",
                            decoder
                        )
                    }
                }
                Err(e) => panic!(
                    "Error in what should be correct input: {:#?}, decoder: {:#?}",
                    e, decoder
                ),
            }
        } else if found_length && pair[1] < buf_bytes.len() {
            match decoder.decode(&mut buf) {
                Ok(opt) => {
                    if opt.is_some() {
                        panic!(
                            "Decoder doesn't have enough input to be printing some yet. decoder: {:#?}",
                            decoder
                        )
                    }
                }
                Err(e) => panic!(
                    "Error in what should be correct input: {:#?}, decoder: {:#?}",
                    e, decoder
                ),
            }
        } else if found_length && pair[1] == buf_bytes.len() {
            match decoder.decode(&mut buf) {
                Ok(opt) => match opt {
                    Some(item) => assert_eq!(&item.bytes[..], data),
                    None => {
                        let mut pretty_print = BytesMut::with_capacity(0);
                        pretty_print
                            .extend(length_bytes.iter().chain([b':'].iter()).chain(data.iter()));
                        panic!(
                                    "Should parse correctly and not give a none.\ninput: {:#?}\nlength: {:#?}\nfinal state of buffer: {:#?}\ndecoder: {:#?}",
                                    pretty_print,
                                    data.len(),
                                    buf,
                                    decoder
                                );
                    }
                },
                Err(e) => panic!(
                    "Error in what should be correct input: {:#?}, decoder: {:#?}",
                    e, decoder
                ),
            }
        } else {
            match decoder.decode(&mut buf) {
                Ok(opt) => {
                    if opt.is_some() {
                        panic!(
                            "Decode should just read the length. decoder: {:#?}",
                            decoder
                        )
                    }
                }
                Err(e) => panic!(
                    "Error in what should be correct input: {:#?}, decoder: {:#?}",
                    e, decoder
                ),
            }
        }
    }
});

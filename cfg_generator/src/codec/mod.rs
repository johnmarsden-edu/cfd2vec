use bytes::{Buf, Bytes, BytesMut};
use std::fmt::{Display, Formatter};

use std::io::Error;
use std::u64;
use tokio_util::codec::Decoder;

#[derive(Debug)]
pub struct SizedDataCodec {
    next_index: usize,
    current_length: Option<u64>,
}

#[derive(Debug)]
pub enum SizedDataCodecError {
    InvalidChunkSize(String),
    LengthOverflow(usize, usize),
    Io(Error),
}

impl Display for SizedDataCodecError {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            SizedDataCodecError::InvalidChunkSize(message) => {
                write!(f, "Invalid chunk decoded: {}", message)
            }
            SizedDataCodecError::Io(e) => {
                write!(f, "{}", e)
            }
            SizedDataCodecError::LengthOverflow(attempted_read_length, size) => {
                write!(
                    f,
                    "length {} overflowed above {} which is the possible size that a usize can represent",
                    attempted_read_length,
                    size
                )
            }
        }
    }
}

impl From<Error> for SizedDataCodecError {
    fn from(e: Error) -> Self {
        SizedDataCodecError::Io(e)
    }
}

impl std::error::Error for SizedDataCodecError {}

impl SizedDataCodec {
    pub fn new() -> Self {
        SizedDataCodec {
            next_index: 0,
            current_length: None,
        }
    }
}

impl Default for SizedDataCodec {
    fn default() -> Self {
        SizedDataCodec::new()
    }
}

trait SizedDataDecoder {
    type Item;
    type Error;
    fn read_size(&mut self, src: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error>;
    fn read_in_data(&mut self, src: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error>;
}

impl SizedDataDecoder for SizedDataCodec {
    type Item = DecoderItem;
    type Error = DecoderError;
    fn read_size(&mut self, src: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error> {
        const OFFSET: usize = 8;

        if src.len() >= OFFSET {
            let mut bites = [0; OFFSET];
            // println!("to offset: {:#?}", src[..OFFSET].to_vec());
            for (index, bite) in src[..OFFSET].iter().enumerate() {
                bites[index] = *bite;
            }
            // println!("bites: {:#?}", bites);
            self.current_length = Some(u64::from_be_bytes(bites));
            src.advance(OFFSET);
            self.next_index = 0;
        }
        Ok(None)
    }

    fn read_in_data(&mut self, src: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error> {
        if (src.len() as u64) < self.current_length.unwrap() {
            Ok(None)
        } else {
            let mut chunk = src.split_to(self.current_length.unwrap() as usize);
            self.current_length = None;
            chunk.truncate(chunk.len());
            let chunk = chunk.freeze();
            Ok(Some(DecoderItem {
                length: chunk.len(),
                bytes: chunk,
            }))
        }
    }
}

pub struct DecoderItem {
    pub bytes: Bytes,
    pub length: usize,
}
type DecoderError = SizedDataCodecError;

impl Decoder for SizedDataCodec {
    type Item = DecoderItem;
    type Error = DecoderError;

    fn decode(&mut self, src: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error> {
        match self.current_length {
            None => self.read_size(src),
            Some(_) => self.read_in_data(src),
        }
    }
}

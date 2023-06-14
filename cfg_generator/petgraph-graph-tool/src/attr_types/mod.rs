use derive_more::From;

/// A trait that all graph-tool types that are supported must implement
pub trait GtTypes {
    /// Returns the appropriate type byte for that graph-tool type
    fn get_type_byte() -> u8;

    /// Returns the length of the type if it has a length that can be used for
    /// telling graph-tool how many bytes there are
    fn get_length(&self) -> Option<usize>;

    /// This returns the number of bytes that are contained within this item
    fn get_num_bytes(&self) -> u64;

    /// Returns the vector of bytes that represent the value being stored within
    fn get_value_bytes(&self) -> Vec<u8>;
}

/// A boolean type
#[derive(From, Copy, Clone, Debug)]
pub struct Bool(bool);
impl GtTypes for Bool {
    fn get_type_byte() -> u8 {
        0x00
    }

    fn get_length(&self) -> Option<usize> {
        None
    }

    fn get_num_bytes(&self) -> u64 {
        1
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        vec![if self.0 { 0x01 } else { 0x00 }]
    }
}

/// An int16_t type
#[derive(From, Copy, Clone, Debug)]
pub struct Int16(i16);
impl GtTypes for Int16 {
    fn get_type_byte() -> u8 {
        0x01
    }

    fn get_length(&self) -> Option<usize> {
        None
    }

    fn get_num_bytes(&self) -> u64 {
        2
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.to_le_bytes().to_vec()
    }
}

/// An int32_t type
#[derive(From, Copy, Clone, Debug)]
pub struct Int32(i32);
impl GtTypes for Int32 {
    fn get_type_byte() -> u8 {
        0x02
    }

    fn get_length(&self) -> Option<usize> {
        None
    }

    fn get_num_bytes(&self) -> u64 {
        4
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.to_le_bytes().to_vec()
    }
}

/// An int64_t type
#[derive(From, Copy, Clone, Debug)]
pub struct Int64(i64);
impl GtTypes for Int64 {
    fn get_type_byte() -> u8 {
        0x03
    }

    fn get_length(&self) -> Option<usize> {
        None
    }

    fn get_num_bytes(&self) -> u64 {
        8
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.to_le_bytes().to_vec()
    }
}

/// A double type
#[derive(From, Copy, Clone, Debug)]
pub struct Double(f64);
impl GtTypes for Double {
    fn get_type_byte() -> u8 {
        0x04
    }

    fn get_length(&self) -> Option<usize> {
        None
    }

    fn get_num_bytes(&self) -> u64 {
        8
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.to_le_bytes().to_vec()
    }
}

// struct LongDouble(f128);

/// A string type
#[derive(From, Clone, Debug)]
pub struct GtString(String);
impl GtTypes for GtString {
    fn get_type_byte() -> u8 {
        0x06
    }

    fn get_length(&self) -> Option<usize> {
        Some(self.0.as_bytes().len())
    }

    fn get_num_bytes(&self) -> u64 {
        8 + (self.0.as_bytes().len() as u64)
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.as_bytes().to_vec()
    }
}

/// A vector of bools type
#[derive(From, Clone, Debug)]
pub struct VecBool(Vec<bool>);
impl GtTypes for VecBool {
    fn get_type_byte() -> u8 {
        0x07
    }

    fn get_length(&self) -> Option<usize> {
        Some(self.0.len())
    }

    fn get_num_bytes(&self) -> u64 {
        8 + (self.0.len() as u64)
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0
            .iter()
            .map(|b| if *b { 0x01 } else { 0x00 })
            .collect()
    }
}

/// A vector of int16_t types
#[derive(From, Clone, Debug)]
pub struct VecInt16(Vec<i16>);
impl GtTypes for VecInt16 {
    fn get_type_byte() -> u8 {
        0x08
    }

    fn get_length(&self) -> Option<usize> {
        Some(self.0.len())
    }

    fn get_num_bytes(&self) -> u64 {
        8 + 2 * (self.0.len() as u64)
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.iter().flat_map(|i| i.to_le_bytes()).collect()
    }
}

/// A vector of int32_t types
#[derive(From, Clone, Debug)]
pub struct VecInt32(Vec<i32>);
impl GtTypes for VecInt32 {
    fn get_type_byte() -> u8 {
        0x09
    }

    fn get_length(&self) -> Option<usize> {
        Some(self.0.len())
    }

    fn get_num_bytes(&self) -> u64 {
        8 + 4 * (self.0.len() as u64)
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.iter().flat_map(|i| i.to_le_bytes()).collect()
    }
}

/// A vector of int64_t types
#[derive(From, Clone, Debug)]
pub struct VecInt64(Vec<i64>);
impl GtTypes for VecInt64 {
    fn get_type_byte() -> u8 {
        0x0a
    }

    fn get_length(&self) -> Option<usize> {
        Some(self.0.len())
    }

    fn get_num_bytes(&self) -> u64 {
        8 + 8 * (self.0.len() as u64)
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.iter().flat_map(|i| i.to_le_bytes()).collect()
    }
}

/// A vector of double types
#[derive(From, Clone, Debug)]
pub struct VecDouble(Vec<f64>);
impl GtTypes for VecDouble {
    fn get_type_byte() -> u8 {
        0x0b
    }

    fn get_length(&self) -> Option<usize> {
        Some(self.0.len())
    }

    fn get_num_bytes(&self) -> u64 {
        8 + 8 * (self.0.len() as u64)
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.iter().flat_map(|f| f.to_le_bytes()).collect()
    }
}

// struct VecLongDouble(Vec<f128>);

/// A vector of string types
#[derive(From, Clone, Debug)]
pub struct VecString(Vec<String>);
impl GtTypes for VecString {
    fn get_type_byte() -> u8 {
        0x0d
    }

    fn get_length(&self) -> Option<usize> {
        Some(self.0.len())
    }

    fn get_num_bytes(&self) -> u64 {
        let num_bytes_in_all_string: u64 = self.0.iter().map(|s| s.as_bytes().len() as u64).sum();
        8 + 8 * (self.0.len() as u64) + num_bytes_in_all_string
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.iter().flat_map(|s| s.as_bytes().to_vec()).collect()
    }
}

/// A pickled Python object. Most of the time, unless you are reading in graph-tool files
/// this will not be something you want to generate by hand
#[derive(From, Clone, Debug)]
pub struct PickledPythonObject(Vec<u8>);
impl GtTypes for PickledPythonObject {
    fn get_type_byte() -> u8 {
        0x0e
    }

    fn get_length(&self) -> Option<usize> {
        Some(self.0.len())
    }

    fn get_num_bytes(&self) -> u64 {
        8 + (self.0.len() as u64)
    }

    fn get_value_bytes(&self) -> Vec<u8> {
        self.0.clone()
    }
}

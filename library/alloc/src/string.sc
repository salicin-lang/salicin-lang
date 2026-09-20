let Vec = alloc.vec.Vec

/// Owns bytes rejected by UTF-8 validation and the valid-prefix length.
pub let FromUtf8Error = struct {
  bytes: Vec<u8>,
  valid_prefix: u64,
}

extend(FromUtf8Error) {
  /// Returns the length of the valid UTF-8 prefix.
  let valid_up_to = { (self: Borrow<self>)(): u64 =>  self.valid_prefix }

  /// Recovers ownership of the rejected bytes.
  let into_bytes = { (move self)(): Vec<u8> =>  self.bytes }
}

let first_invalid_owned_utf8 = { (bytes: Borrow<Vec<u8>>): core.Option<u64> =>
    let source = bytes.as_slice<shared>()
  core.string.str.first_invalid_utf8(source)
}

/// Validates and consumes bytes, transferring their allocation on success and
/// returning the original owner on failure.
pub let string_from_utf8 = { (
    move bytes: Vec<u8>,
  ): core.Result<FromUtf8Error><core.string.String> =>
    match(first_invalid_owned_utf8(bytes)) {
    Some(valid_up_to) => do {
      core.Result.Err(FromUtf8Error { bytes: bytes, valid_prefix: valid_up_to })
    }, None => do {
      if(bytes.is_empty()) {
        core.Result.Ok("")
      } else: {
        let parts = alloc.vec.vec_into_raw_parts<u8>(bytes)
        let text = unsafe {
          core.string.string_from_raw_parts(parts.0, parts.1, parts.2)
        }
        core.Result.Ok(text)
      }
    },
  }
}

/// Consumes a String and returns owned bytes. Heap storage transfers without
/// copying; static literal storage is copied into a fresh vector.
pub let string_into_bytes = { (
    move value: core.string.String,
  ): Vec<u8> =>
    let parts = unsafe {
    core.string.string_into_raw_parts(value)
  }
  if(parts.2 == 0) {
    let mut bytes = Vec<u8>.with_capacity(parts.1)
    let mut index: u64 = 0
    while(index < parts.1) {
      let byte = unsafe {
        *raw_offset(parts.0, index)
      }
      bytes.push(byte)
      index = index + 1
    }
    bytes
  } else: {
    unsafe {
      alloc.vec.vec_from_raw_parts<u8>(parts.0, parts.1, parts.2)
    }
  }
}

/// Allocation-backed UTF-8 writer used by Display and Debug formatting.
pub let StringWriter = struct {
  value: core.string.String,
}

extend(StringWriter) {
  /// Creates an empty writer without allocating.
  let new = { (): StringWriter =>
      StringWriter { value: core.string.String.new() }
  }

  /// Creates an empty writer with reserved UTF-8 byte capacity.
  let with_capacity = { (capacity: u64): StringWriter =>
      StringWriter { value: core.string.String.with_capacity(capacity) }
  }

  /// Borrows the text written so far.
  let as_str = { <r: region>
      (self: Borrow<r><self>)(): Borrow<r><core.string.str> =>
      self.value.as_str()
  }

  /// Returns the completed owned String.
  let finish = { (move self)(): core.string.String =>  self.value }
}

extend(StringWriter, core.fmt.TextWriter<pure>) {
  let write_scalar = { (self: Borrow<mut><self>)
      (value: core.string.UnicodeScalar): () =>
      self.value.push(value)
  }

  let write_ascii = { (self: Borrow<mut><self>)(value: u8): () =>
      if(value > 127) {
      unsafe {
        raw_trap()
      }
    }
    let mut source = value
    let mut code: u32 = 0
    while(source != 0) {
      source = source - 1
      code = code + 1
    }
    match(core.string.UnicodeScalar.from_u32(code)) {
      Some(scalar) => self.value.push(scalar), None => do {
        unsafe { raw_trap() }
      },
    }
  }
}

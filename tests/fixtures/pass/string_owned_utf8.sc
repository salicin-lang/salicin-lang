let Vec = alloc.Vec

let valid_conversion(): bool = {
  let mut bytes = Vec<u8>.with_capacity(8)
  bytes.push(65)
  bytes.push(230)
  bytes.push(159)
  bytes.push(179)
  match(alloc.string.string_from_utf8(bytes)) {
    Ok(text) => do {
      let expected: String = "A柳"
      let equal = text == expected
      let recovered = alloc.string.string_into_bytes(text)
      equal &&
        recovered.len() == 4 &&
        recovered.capacity() == 8 &&
        recovered.read(0) == 65 &&
        recovered.read(1) == 230 &&
        recovered.read(2) == 159 &&
        recovered.read(3) == 179
    },
    Err(_) => false,
  }
}

let invalid_conversion(): bool = {
  let mut bytes = Vec<u8>.with_capacity(7)
  bytes.push(65)
  bytes.push(226)
  bytes.push(40)
  bytes.push(161)
  match(alloc.string.string_from_utf8(bytes)) {
    Ok(_) => false,
    Err(error) => do {
      let valid_up_to = error.valid_up_to()
      let recovered = error.into_bytes()
      valid_up_to == 1 &&
        recovered.len() == 4 &&
        recovered.capacity() == 7 &&
        recovered.read(0) == 65 &&
        recovered.read(1) == 226 &&
        recovered.read(2) == 40 &&
        recovered.read(3) == 161
    },
  }
}

let truncated_conversion(): bool = {
  let mut bytes = Vec<u8>.new()
  bytes.push(65)
  bytes.push(226)
  bytes.push(130)
  match(alloc.string.string_from_utf8(bytes)) {
    Ok(_) => false,
    Err(error) => do {
      error.valid_up_to() == 1 && error.into_bytes().len() == 3
    },
  }
}

let edge_conversion(): bool = {
  let empty = Vec<u8>.new()
  let empty_ok = match(alloc.string.string_from_utf8(empty)) {
    Ok(text) => text.is_empty(),
    Err(_) => false,
  }
  let literal: String = "柳"
  let literal_bytes = alloc.string.string_into_bytes(literal)
  empty_ok &&
    literal_bytes.len() == 3 &&
    literal_bytes.read(0) == 230 &&
    literal_bytes.read(1) == 159 &&
    literal_bytes.read(2) == 179
}

let main(): i32 = {
  if(valid_conversion() &&
    invalid_conversion() &&
    truncated_conversion() &&
    edge_conversion() ) {
    42
  } else: {
    0
  }
}

test<"string_owned_utf8.sc"> {
  std.test.assert(main() == 42)
}

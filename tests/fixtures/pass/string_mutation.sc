let scalar(value: u32): core.string.UnicodeScalar = {
  match(core.string.UnicodeScalar.from_u32(value)) {
    Some(value) => value, None => do {
      unsafe {
        raw_trap()
      }
    },
  }
}

let construction_checks(): bool = {
  let source: String = "柳A"
  let source_view = source.as_str()
  let copied = String.from_str(source_view)
  let from_scalar = String.from_unicode_scalar(scalar(128578))
  let expected_scalar: String = "🙂"
  let empty = String.new()
  let reserved = String.with_capacity(12)
  copied == source &&
    copied.capacity() == 4 &&
    from_scalar == expected_scalar &&
    from_scalar.len_bytes() == 4 &&
    empty.is_empty() &&
    empty.capacity() == 0 &&
    reserved.is_empty() &&
    reserved.capacity() == 12
}

let append_checks(): bool = {
  let mut text: String = "A"
  text.reserve(7)
  let reserved = text.capacity() >= 8
  text.push(scalar(26611))
  let suffix: String = "🙂"
  let suffix_view = suffix.as_str()
  text.push_str(suffix_view)
  let expected: String = "A柳🙂"
  let view = text.as_str()
  let boundary = view.is_char_boundary(4)
  reserved &&
    text == expected &&
    text.len_bytes() == 8 &&
    text.capacity() >= text.len_bytes() &&
    boundary
}

let truncation_checks(): bool = {
  let source: String = "A柳🙂"
  let source_view = source.as_str()
  let mut text = String.from_str(source_view)
  let invalid = !text.truncate(2) && text.len_bytes() == 8
  let valid = text.truncate(4)
  let expected: String = "A柳"
  let unchanged = !text.truncate(9) && text == expected
  text.clear()
  invalid &&
    valid &&
    unchanged &&
    text.is_empty() &&
    text.capacity() == 8
}

let main(): i32 = {
  if construction_checks() &&
    append_checks() &&
    truncation_checks() {
    42
  } else {
    0
  }
}

test("string_mutation.sc") {
  std.test.assert(main() == 42)
}

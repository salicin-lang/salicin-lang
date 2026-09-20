let String = core.string.String
let parse_u64_radix = core.fmt.parse_u64_radix
let parse_i64_radix = core.fmt.parse_i64_radix
let StringWriter = alloc.string.StringWriter

let text_equal = { (left: Borrow<String>, right: Borrow<String>): bool =>
  let left_view = left.as_str()
  let right_view = right.as_str()
  left_view == right_view
}

let parse_hex = { (): bool =>
  let source: String = "ff"
  let view = source.as_str()
  match(parse_u64_radix(view, 16)) { Ok(value) => value == 255, Err(_) => false,
  }
}

let parse_minimum = { (): bool =>
  let source: String = "-9223372036854775808"
  let view = source.as_str()
  match(parse_i64_radix(view, 10)) { Ok(value) => value == -9223372036854775808, Err(_) => false,
  }
}

let rejects_overflow = { (): bool =>
  let source: String = "18446744073709551616"
  let view = source.as_str()
  match(parse_u64_radix(view, 10)) {
    Ok(_) => false, Err(error) => do {
      match(error.kind()) { Overflow => error.offset() == 19, _ => false,
      }
    },
  }
}

let format_u64 = { (value: u64): String =>
  let mut writer = StringWriter.new()
  value.display(writer)
  writer.finish()
}

let format_i64 = { (value: i64): String =>
  let mut writer = StringWriter.new()
  value.display(writer)
  writer.finish()
}

let format_bool = { (value: bool): String =>
  let mut writer = StringWriter.new()
  core.fmt.write_bool(writer)(value)
  writer.finish()
}

let format_scalar = { (value: core.string.UnicodeScalar): String =>
  let mut writer = StringWriter.new()
  value.display(writer)
  writer.finish()
}

let main = { (): i32 =>
  let maximum: u64 = 18446744073709551615
  let minimum: i64 = -9223372036854775808
  let truth: bool = true
  let decimal = format_u64(maximum)
  let expected_decimal: String = "18446744073709551615"
  let signed = format_i64(minimum)
  let expected_signed: String = "-9223372036854775808"
  let boolean = format_bool(truth)
  let expected_boolean: String = "true"
  let scalar = match(core.string.UnicodeScalar.from_u32(128578)) { Some(value) => format_scalar(value), None => "",
  }
  let expected_scalar: String = "🙂"
  if(parse_hex() &&
    parse_minimum() &&
    rejects_overflow() &&
    text_equal(decimal, expected_decimal) &&
    text_equal(signed, expected_signed) &&
    text_equal(boolean, expected_boolean) &&
    text_equal(scalar, expected_scalar) ) {
    42
  } else: {
    0
  }
}

test("formatting.sc") {
  std.test.assert(main() == 42)
}

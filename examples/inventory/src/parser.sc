let parse_i64_radix = core.fmt.parse_i64_radix

/// Parses one strict decimal command-line field.
pub let decimal: (
  value: Borrow<core.string.str>,
): core.Result<core.fmt.ParseIntError><i64> = {
  parse_i64_radix(value, 10)
}

test("decimal parser accepts signed input") {
  let input: String = "-17"
  let view = input.as_str()
  match(decimal(view)) {
    Ok(value) => std.test.assert(value == -17),
    Err(_) => std.test.fail("expected parsed -17"),
  }
}

test("decimal parser rejects trailing text") {
  let input: String = "12x"
  let view = input.as_str()
  match(decimal(view)) {
    Ok(_) => std.test.fail("expected InvalidDigit"),
    Err(error) => do {
      match(error.kind()) {
        InvalidDigit => (),
        _ => std.test.fail("expected InvalidDigit"),
      }
    },
  }
}

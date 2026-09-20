/// Pure text parsing with a statically selected output and error type.
pub let Parse = trait {
  /// Borrowed source type; standard parsing implementations bind this to
  /// `core.string.str`.
  let Source: type
  /// Structured error reported for malformed or out-of-range input.
  let Error: type

  /// Parses the complete borrowed text. Implementations do not allocate or
  /// accept leading or trailing input unless their concrete contract says so.
  let parse = { <r: region>
      (value: Borrow<r><Source>): core.Result<Error><self> }
}

/// Effect-polymorphic sink for validated UTF-8 fragments.
pub let TextWriter = <e: effects> trait {
  /// Writes one Unicode scalar without requiring a temporary allocation.
  let write_scalar = { with<e>(self: Borrow<mut><self>)(value: core.string.UnicodeScalar): () }
  let write_ascii = { with<e>(self: Borrow<mut><self>)(value: u8): () }
}

/// Stable categories for strict integer parsing failures.
pub let ParseIntErrorKind = enum {
  Empty,
  InvalidRadix,
  InvalidSign,
  InvalidDigit,
  Overflow,
}

extend(ParseIntErrorKind, core.marker.Copyable) {}

/// A strict integer parsing failure and its first failing UTF-8 byte offset.
pub let ParseIntError = struct {
  failure: ParseIntErrorKind,
  byte_offset: u64,
}

extend(ParseIntError) {
  let kind = { (self: Borrow<self>)(): ParseIntErrorKind =>  self.failure }
  let offset = { (self: Borrow<self>)(): u64 =>  self.byte_offset }
}

let parse_failure = { (
    failure: ParseIntErrorKind,
    byte_offset: u64,
  ): core.Result<ParseIntError><u64> =>
    core.Result.Err(ParseIntError {
    failure: failure,
    byte_offset: byte_offset,
  })
}

let digit_value = { (byte: u8): core.Option<u8> =>
    if(byte >= 48 && byte <= 57) {
    core.Option.Some(byte - 48)
  } else: {
    if(byte >= 65 && byte <= 90) {
      core.Option.Some(byte - 65 + 10)
    } else: {
      if(byte >= 97 && byte <= 122) {
        core.Option.Some(byte - 97 + 10)
      } else: {
        core.Option.None
      }
    }
  }
}

let byte_at = { (value: Borrow<core.string.str>, index: u64): u8 =>
    let bytes = value.as_bytes()
  let byte = bytes.at(index)
  byte
}

let widen_u8 = { (value: u8): u64 =>
    let mut source = value
  let mut output: u64 = 0
  while(source != 0) {
    source = source - 1
    output = output + 1
  }
  output
}

let parse_magnitude = { (
    value: Borrow<core.string.str>,
    start: u64,
    radix: u8,
    limit: u64,
  ): core.Result<ParseIntError><u64> =>
    if(radix < 2 || radix > 36) {
    return(parse_failure(InvalidRadix, 0))
  }
  if(start == value.len()) {
    return(parse_failure(Empty, start))
  }
  let base = widen_u8(radix)
  let mut output: u64 = 0
  let mut index = start
  while(index < value.len()) {
    let digit = match(digit_value(byte_at(value, index))) { Some(digit) => digit, None => return(parse_failure(InvalidDigit, index)),
    }
    if(digit >= radix) {
      return(parse_failure(InvalidDigit, index))
    }
    let wide_digit = widen_u8(digit)
    if(output > (limit - wide_digit) / base) {
      return(parse_failure(Overflow, index))
    }
    output = output * base + wide_digit
    index = index + 1
  }
  core.Result.Ok(output)
}

/// Parses an unsigned integer in radix 2 through 36.
pub let parse_u64_radix = { (
    value: Borrow<core.string.str>,
    radix: u8,
  ): core.Result<ParseIntError><u64> =>
    if(value.is_empty()) {
    return(core.Result.Err(ParseIntError {
      failure: Empty,
      byte_offset: 0,
    }))
  }
  let first = byte_at(value, 0)
  if(first == 43 || first == 45) {
    return(core.Result.Err(ParseIntError {
      failure: InvalidSign,
      byte_offset: 0,
    }))
  }
  match(parse_magnitude(value, 0, radix, 18446744073709551615)) { Ok(magnitude) => core.Result.Ok(magnitude), Err(error) => core.Result.Err(error),
  }
}

/// Parses a signed integer in radix 2 through 36.
pub let parse_i64_radix = { (
    value: Borrow<core.string.str>,
    radix: u8,
  ): core.Result<ParseIntError><i64> =>
    if(radix < 2 || radix > 36) {
    return(core.Result.Err(ParseIntError {
      failure: InvalidRadix,
      byte_offset: 0,
    }))
  }
  if(value.is_empty()) {
    return(core.Result.Err(ParseIntError {
      failure: Empty,
      byte_offset: 0,
    }))
  }
  let first = byte_at(value, 0)
  let negative = first == 45
  let start: u64 = if(negative || first == 43) { 1 } else: { 0 }
  if(start == value.len()) {
    return(core.Result.Err(ParseIntError {
      failure: Empty,
      byte_offset: start,
    }))
  }
  let mut base_source = radix
  let mut base: i64 = 0
  while(base_source != 0) {
    base_source = base_source - 1
    base = base + 1
  }
  let mut output: i64 = 0
  let mut index = start
  while(index < value.len()) {
    let digit = match(digit_value(byte_at(value, index))) {
      Some(digit) => digit, None => do { return(core.Result.Err(ParseIntError {
          failure: InvalidDigit,
          byte_offset: index,
        })) },
    }
    if(digit >= radix) {
      return(core.Result.Err(ParseIntError {
        failure: InvalidDigit,
        byte_offset: index,
      }))
    }
    let mut digit_source = digit
    let mut wide_digit: i64 = 0
    while(digit_source != 0) {
      digit_source = digit_source - 1
      wide_digit = wide_digit + 1
    }
    if(negative) {
      if(output < (-9223372036854775808 + wide_digit) / base) {
        return(core.Result.Err(ParseIntError {
          failure: Overflow,
          byte_offset: index,
        }))
      }
      output = output * base - wide_digit
    } else: {
      if(output > (9223372036854775807 - wide_digit) / base) {
        return(core.Result.Err(ParseIntError {
          failure: Overflow,
          byte_offset: index,
        }))
      }
      output = output * base + wide_digit
    }
    index = index + 1
  }
  core.Result.Ok(output)
}

extend(u64, Parse) {
  let Source = core.string.str
  let Error = ParseIntError

  let parse = { <r: region>
      (value: Borrow<r><Source>): core.Result<Error><self> =>
      parse_u64_radix(value, 10)
  }
}

extend(i64, Parse) {
  let Source = core.string.str
  let Error = ParseIntError

  let parse = { <r: region>
      (value: Borrow<r><Source>): core.Result<Error><self> =>
      parse_i64_radix(value, 10)
  }
}

let write_digit = { <e: effects, W: type>with<e>(writer: Borrow<mut><W>)(digit: u8): () requires(W is TextWriter<e>) =>
    writer.write_ascii(48 + digit)
}

let write_unsigned = { <e: effects, W: type>with<e>(writer: Borrow<mut><W>)(value: u128): () requires(W is TextWriter<e>) =>
    if(value >= 10) {
    write_unsigned(writer)(value / 10)
  }
  let remainder = value % 10
  let digit: u8 = if(remainder == 0) { 0 }
  else: {
    if(remainder == 1) { 1 }
    else: {
      if(remainder == 2) { 2 }
      else: {
        if(remainder == 3) { 3 }
        else: {
          if(remainder == 4) { 4 }
          else: {
            if(remainder == 5) { 5 }
            else: {
              if(remainder == 6) { 6 }
              else: {
                if(remainder == 7) { 7 }
                else: {
                  if(remainder == 8) { 8 }
                  else: { 9 }
                }
              }
            }
          }
        }
      }
    }
  }
  write_digit(writer)(digit)
}

let write_minus = { <e: effects, W: type>with<e>(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
    writer.write_ascii(45)
}

let display_unsigned = { <e: effects, W: type>with<e>(writer: Borrow<mut><W>)(value: u128): () requires(W is TextWriter<e>) =>
    write_unsigned(writer)(value)
}

let display_signed = { <e: effects, W: type>with<e>(writer: Borrow<mut><W>)(negative: bool, magnitude: u128): () requires(W is TextWriter<e>) =>
    if(negative) {
    write_minus(writer)
  }
  write_unsigned(writer)(magnitude)
}

let write_unsigned_u64 = { <e: effects, W: type>with<e>(writer: Borrow<mut><W>)(value: u64): () requires(W is TextWriter<e>) =>
    if(value >= 10) {
    write_unsigned_u64(writer)(value / 10)
  }
  let remainder = value % 10
  let digit: u8 = if(remainder == 0) { 0 }
  else: {
    if(remainder == 1) { 1 }
    else: {
      if(remainder == 2) { 2 }
      else: {
        if(remainder == 3) { 3 }
        else: {
          if(remainder == 4) { 4 }
          else: {
            if(remainder == 5) { 5 }
            else: {
              if(remainder == 6) { 6 }
              else: {
                if(remainder == 7) { 7 }
                else: {
                  if(remainder == 8) { 8 }
                  else: { 9 }
                }
              }
            }
          }
        }
      }
    }
  }
  write_digit(writer)(digit)
}

let display_signed_i64 = { <e: effects, W: type>with<e>(writer: Borrow<mut><W>)(negative: bool, magnitude: u64): () requires(W is TextWriter<e>) =>
    if(negative) {
    write_minus(writer)
  }
  write_unsigned_u64(writer)(magnitude)
}

/// Writes the canonical lowercase boolean spelling.
pub let write_bool = { <e: effects, W: type>with<e>(writer: Borrow<mut><W>)(value: bool): () requires(W is TextWriter<e>) =>
    if(value) {
    writer.write_ascii(116)
    writer.write_ascii(114)
    writer.write_ascii(117)
    writer.write_ascii(101)
  } else: {
    writer.write_ascii(102)
    writer.write_ascii(97)
    writer.write_ascii(108)
    writer.write_ascii(115)
    writer.write_ascii(101)
  }
}

extend(bool, Display) {
  let display = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: bool = self
    write_bool(writer)(value)
  }
}

extend(core.string.UnicodeScalar, Display) {
  let display = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: core.string.UnicodeScalar = self
    writer.write_scalar(value)
  }
}

extend(core.string.str, Display) {
  let display = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let mut scalars = self.scalars()
    loop {
      match(scalars.next()) {
        Some(scalar) => writer.write_scalar(scalar), None => break(),
      }
    }
  }
}

extend(core.string.String, Display) {
  let display = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let view = self.as_str()
    let mut scalars = view.scalars()
    loop {
      match(scalars.next()) {
        Some(scalar) => writer.write_scalar(scalar), None => break(),
      }
    }
  }
}

extend(u64, Display) {
  let display = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: u64 = self
    write_unsigned_u64(writer)(value)
  }
}

extend(u128, Display) {
  let display = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: u128 = self
    display_unsigned(writer)(value)
  }
}

extend(i64, Display) {
  let display = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: i64 = self
    display_signed_i64(writer)(value < 0, value.magnitude())
  }
}

extend(i128, Display) {
  let display = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: i128 = self
    display_signed(writer)(value < 0, value.magnitude())
  }
}

extend(bool, Debug) {
  let debug = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: bool = self
    write_bool(writer)(value)
  }
}

extend(core.string.UnicodeScalar, Debug) {
  let debug = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: core.string.UnicodeScalar = self
    writer.write_scalar(value)
  }
}

extend(core.string.str, Debug) {
  let debug = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let mut scalars = self.scalars()
    loop {
      match(scalars.next()) {
        Some(scalar) => writer.write_scalar(scalar), None => break(),
      }
    }
  }
}

extend(core.string.String, Debug) {
  let debug = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let view = self.as_str()
    let mut scalars = view.scalars()
    loop {
      match(scalars.next()) {
        Some(scalar) => writer.write_scalar(scalar), None => break(),
      }
    }
  }
}

extend(u64, Debug) {
  let debug = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: u64 = self
    write_unsigned_u64(writer)(value)
  }
}

extend(u128, Debug) {
  let debug = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: u128 = self
    write_unsigned(writer)(value)
  }
}

extend(i64, Debug) {
  let debug = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: i64 = self
    display_signed_i64(writer)(value < 0, value.magnitude())
  }
}

extend(i128, Debug) {
  let debug = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) =>
      let value: i128 = self
    display_signed(writer)(value < 0, value.magnitude())
  }
}

/// Source-backed user-facing formatting.
pub let Display = trait {
  /// Writes a deterministic Display representation without reflection.
  let display = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) }
}

/// Source-backed diagnostic formatting.
pub let Debug = trait {
  /// Writes a deterministic diagnostic representation without reflection.
  let debug = { <e: effects, W: type>with<e>(self: Borrow<self>)(writer: Borrow<mut><W>): () requires(W is TextWriter<e>) }
}

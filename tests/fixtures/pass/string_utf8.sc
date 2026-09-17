let greeting: String = "柳"

let runtime_text(): String = {
  "salicin"
}

let is_scalar(expected_value: u32, expected_length: u64): bool = {
  match core.string.UnicodeScalar.from_u32(expected_value)
    { Some(scalar) ->
      scalar.to_u32() == expected_value && scalar.len_utf8() == expected_length
    }
    { None -> false }
}

let scalar_checks(): bool = {
  let equality = match core.string.UnicodeScalar.from_u32(65)
    { Some(a) ->
      match core.string.UnicodeScalar.from_u32(65)
      { Some(another_a) ->
        match core.string.UnicodeScalar.from_u32(66)
        { Some(b) -> a == another_a && a != b }
        { None -> false }
      }
      { None -> false }
    }
    { None -> false }
  equality &&
    is_scalar(0, 1) &&
    is_scalar(127, 1) &&
    is_scalar(128, 2) &&
    is_scalar(2047, 2) &&
    is_scalar(2048, 3) &&
    is_scalar(55295, 3) &&
    is_scalar(57344, 3) &&
    is_scalar(65535, 3) &&
    is_scalar(65536, 4) &&
    is_scalar(1114111, 4) &&
    core.string.UnicodeScalar.from_u32(55296).is_none() &&
    core.string.UnicodeScalar.from_u32(57343).is_none() &&
    core.string.UnicodeScalar.from_u32(1114112).is_none()
}

let accepts_utf8(bytes: Borrow<core.memory.Slice<u8>>, expected_length: u64): bool = {
  match core.string.str.from_utf8(bytes)
    { Some(text) ->
      let encoded = text.as_bytes()
      text.len() == expected_length &&
        text.is_empty() == (expected_length == 0) &&
        encoded.len() == expected_length
    }
    { None -> false }
}

let rejects_utf8(bytes: Borrow<core.memory.Slice<u8>>): bool = {
  core.string.str.from_utf8(bytes).is_none()
}

let borrowed_text_checks(): bool = {
  let empty: Array<u8><0> = []
  let ascii: Array<u8><1> = [65]
  let two_byte: Array<u8><2> = [194, 128]
  let three_byte: Array<u8><3> = [224, 160, 128]
  let before_surrogates: Array<u8><3> = [237, 159, 191]
  let after_surrogates: Array<u8><3> = [238, 128, 128]
  let four_byte: Array<u8><4> = [240, 144, 128, 128]
  let maximum_scalar: Array<u8><4> = [244, 143, 191, 191]

  let continuation: Array<u8><1> = [128]
  let overlong_two: Array<u8><2> = [192, 128]
  let truncated_three: Array<u8><2> = [226, 130]
  let overlong_three: Array<u8><3> = [224, 128, 128]
  let surrogate: Array<u8><3> = [237, 160, 128]
  let overlong_four: Array<u8><4> = [240, 128, 128, 128]
  let above_unicode: Array<u8><4> = [244, 144, 128, 128]
  let invalid_lead: Array<u8><4> = [245, 128, 128, 128]

  let empty_view: Borrow<core.memory.Slice<u8>> = borrow(empty)
  let ascii_view: Borrow<core.memory.Slice<u8>> = borrow(ascii)
  let two_byte_view: Borrow<core.memory.Slice<u8>> = borrow(two_byte)
  let three_byte_view: Borrow<core.memory.Slice<u8>> = borrow(three_byte)
  let before_surrogates_view: Borrow<core.memory.Slice<u8>> = borrow(before_surrogates)
  let after_surrogates_view: Borrow<core.memory.Slice<u8>> = borrow(after_surrogates)
  let four_byte_view: Borrow<core.memory.Slice<u8>> = borrow(four_byte)
  let maximum_scalar_view: Borrow<core.memory.Slice<u8>> = borrow(maximum_scalar)
  let continuation_view: Borrow<core.memory.Slice<u8>> = borrow(continuation)
  let overlong_two_view: Borrow<core.memory.Slice<u8>> = borrow(overlong_two)
  let truncated_three_view: Borrow<core.memory.Slice<u8>> = borrow(truncated_three)
  let overlong_three_view: Borrow<core.memory.Slice<u8>> = borrow(overlong_three)
  let surrogate_view: Borrow<core.memory.Slice<u8>> = borrow(surrogate)
  let overlong_four_view: Borrow<core.memory.Slice<u8>> = borrow(overlong_four)
  let above_unicode_view: Borrow<core.memory.Slice<u8>> = borrow(above_unicode)
  let invalid_lead_view: Borrow<core.memory.Slice<u8>> = borrow(invalid_lead)

  accepts_utf8(empty_view, 0) &&
    accepts_utf8(ascii_view, 1) &&
    accepts_utf8(two_byte_view, 2) &&
    accepts_utf8(three_byte_view, 3) &&
    accepts_utf8(before_surrogates_view, 3) &&
    accepts_utf8(after_surrogates_view, 3) &&
    accepts_utf8(four_byte_view, 4) &&
    accepts_utf8(maximum_scalar_view, 4) &&
    rejects_utf8(continuation_view) &&
    rejects_utf8(overlong_two_view) &&
    rejects_utf8(truncated_three_view) &&
    rejects_utf8(overlong_three_view) &&
    rejects_utf8(surrogate_view) &&
    rejects_utf8(overlong_four_view) &&
    rejects_utf8(above_unicode_view) &&
    rejects_utf8(invalid_lead_view)
}

let string_view_checks(): bool = {
  let text: String = "柳"
  let view = text.as_str()
  let encoded = view.as_bytes()
  view.len() == 3 && !view.is_empty() && encoded.len() == 3
}

let subview_checks(): bool = {
  let text: String = "A柳𐀀"
  let ascii_expected: String = "A"
  let three_byte_expected: String = "柳"
  let four_byte_expected: String = "𐀀"
  let empty_text: String = ""
  let view = text.as_str()
  let ascii_expected_view = ascii_expected.as_str()
  let three_byte_expected_view = three_byte_expected.as_str()
  let four_byte_expected_view = four_byte_expected.as_str()
  let empty_view = empty_text.as_str()
  let boundaries =
    view.is_char_boundary(0) &&
    view.is_char_boundary(1) &&
    !view.is_char_boundary(2) &&
    !view.is_char_boundary(3) &&
    view.is_char_boundary(4) &&
    !view.is_char_boundary(5) &&
    !view.is_char_boundary(6) &&
    !view.is_char_boundary(7) &&
    view.is_char_boundary(8) &&
    !view.is_char_boundary(9)
  let valid = match view.get(0, 8)
    { Some(whole) ->
      match view.get(0, 1)
      { Some(ascii) ->
        match view.get(1, 4)
        { Some(three_byte) ->
          match view.get(4, 8)
          { Some(four_byte) ->
            match view.get(8, 8)
            { Some(empty) ->
              whole == view &&
                ascii == ascii_expected_view &&
                three_byte == three_byte_expected_view &&
                four_byte == four_byte_expected_view &&
                empty.is_empty()
            }
            { None -> false }
          }
          { None -> false }
        }
        { None -> false }
      }
      { None -> false }
    }
    { None -> false }
  boundaries &&
    valid &&
    view.get(2, 4).is_none() &&
    view.get(1, 5).is_none() &&
    view.get(9, 9).is_none() &&
    view.get(4, 1).is_none() &&
    match empty_view.get(0, 0)
    { Some(empty) -> empty.is_empty() }
    { None -> false }
}

let text_equality_checks(): bool = {
  let composed: String = "é"
  let same: String = "é"
  let decomposed: String = "é"
  let longer: String = "é!"
  let same_length_different: String = "ê"
  let view = composed.as_str()
  let same_view = same.as_str()
  let decomposed_view = decomposed.as_str()
    composed == same &&
    composed != decomposed &&
    composed != longer &&
    composed != same_length_different &&
    view == same_view &&
    view != decomposed_view
}

let main(): i32 = {
  let text = runtime_text()
  if text.len_bytes() == 7 &&
    greeting.len_bytes() == 3 &&
    scalar_checks() &&
    borrowed_text_checks() &&
    string_view_checks() &&
    subview_checks() &&
    text_equality_checks() {
    42
  } else {
    0
  }
}

test("string_utf8.sc") {
  std.test.assert(main() == 42)
}

let scalar_is(
  value: core.Option<core.string.UnicodeScalar>,
  expected: u32,
): bool = {
  match(value) { Some(value) => value.to_u32() == expected, None => false,
  }
}

let byte_checks(): bool = {
  let text: String = "A柳"
  let view = text.as_str()
  let mut bytes = view.bytes()
  match(bytes.next()) {
    Some(first) => do {
      match(bytes.next()) {
        Some(second) => do {
          first == 65 &&
            second == 230 &&
            bytes.next().is_some() &&
            bytes.next().is_some() &&
            bytes.next().is_none()
        }, None => false,
      }
    }, None => false,
  }
}

let scalar_checks(): bool = {
  let text: String = "Aé柳🙂"
  let view = text.as_str()
  let mut values = view.scalars()
  scalar_is(values.next(), 65) &&
    scalar_is(values.next(), 233) &&
    scalar_is(values.next(), 26611) &&
    scalar_is(values.next(), 128578) &&
    values.next().is_none() &&
    view.scalar_count() == 4 &&
    scalar_is(view.scalar_at(2), 26611) &&
    view.scalar_at(4).is_none()
}

let main(): i32 = {
  if(byte_checks() && scalar_checks()) {
    42
  } else: {
    0
  }
}

test("string_iteration.sc") {
  std.test.assert(main() == 42)
}

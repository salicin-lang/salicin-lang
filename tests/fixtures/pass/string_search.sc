let option_is(value: core.Option<u64>, expected: u64): bool = {
  match(value) { Some(value) => value == expected, None => false,
  }
}

let borrowed_checks(): bool = {
  let text: String = "A柳B🙂"
  let prefix: String = "A柳"
  let suffix: String = "B🙂"
  let needle: String = "柳B"
  let missing: String = "C"
  let empty: String = ""
  let view = text.as_str()
  let prefix_view = prefix.as_str()
  let suffix_view = suffix.as_str()
  let needle_view = needle.as_str()
  let missing_view = missing.as_str()
  let empty_view = empty.as_str()
  view.starts_with(prefix_view) &&
    view.ends_with(suffix_view) &&
    view.contains(needle_view) &&
    !view.contains(missing_view) &&
    option_is(view.find(needle_view), 1) &&
    option_is(view.find(empty_view), 0)
}

let owning_checks(): bool = {
  let text: String = "A柳B🙂"
  let prefix: String = "A"
  let suffix: String = "🙂"
  let needle: String = "B🙂"
  let prefix_view = prefix.as_str()
  let suffix_view = suffix.as_str()
  let needle_view = needle.as_str()
  let selected = match(text.substring(1, 4)) {
    Some(value) => do {
      let expected: String = "柳"
      value == expected && value.capacity() == 3
    }, None => false,
  }
  let invalid = match(text.substring(2, 4)) { Some(_) => false, None => true,
  }
  selected &&
    invalid &&
    text.starts_with(prefix_view) &&
    text.ends_with(suffix_view) &&
    option_is(text.find(needle_view), 4)
}

let ordering_checks(): bool = {
  let ascii: String = "A"
  let latin: String = "é"
  let cjk: String = "柳"
  let emoji: String = "🙂"
  let ascii_view = ascii.as_str()
  let latin_view = latin.as_str()
  let cjk_view = cjk.as_str()
  let emoji_view = emoji.as_str()
  ascii < latin &&
    latin < cjk &&
    cjk < emoji &&
    ascii_view < latin_view &&
    latin_view < cjk_view &&
    cjk_view < emoji_view
}

let main(): i32 = {
  if borrowed_checks() &&
    owning_checks() &&
    ordering_checks() {
    42
  } else {
    0
  }
}

test("string_search.sc") {
  std.test.assert(main() == 42)
}

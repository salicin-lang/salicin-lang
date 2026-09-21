let main = {
  (): i32 =>
  let mut bytes: Array<u8><1> = [65]
  let source: Borrow<core.memory.Slice<u8>> = borrow(bytes)
  let view: Borrow<core.string.str> = match(core.string.str.from_utf8(source)) {
    Some(text) => text, None => do {
      unsafe { raw_trap() }
    },
  }
  bytes[0] = 66
  if(view.len() == 1) { 42 } else: { 0 }
}

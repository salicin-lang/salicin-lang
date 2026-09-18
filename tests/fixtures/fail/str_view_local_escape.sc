let escape<r: region>
  (anchor: Borrow<r><i32>): Borrow<r><core.string.str> = {
  let bytes: Array<u8><1> = [65]
  let source: Borrow<core.memory.Slice<u8>> = borrow(bytes)
  match(core.string.str.from_utf8(source)) {
    Some(text) => text, None => do {
      unsafe { raw_trap() }
    },
  }
}

let main(): i32 = { 0 }

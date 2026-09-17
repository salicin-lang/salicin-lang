let main(): i32 = {
  let bytes: Array<u8><1> = [65]
  let source: Borrow<core.memory.Slice<u8>> = borrow(bytes)
  raw_str(source)
  0
}

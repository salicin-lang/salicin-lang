let main(): i32 = {
  let mut bytes: Array<u8><1> = [65]
  let source: Borrow<core.memory.Slice<u8>> = borrow(bytes)
  let view: Borrow<core.string.str> = match core.string.str.from_utf8(source)
    { Some(text) -> text }
    { None -> unsafe { raw_trap() } }
  let subview: Borrow<core.string.str> = match view.get(0, 1)
    { Some(text) -> text }
    { None -> unsafe { raw_trap() } }
  bytes[0] = 66
  if subview.len() == 1 { 42 } else { 0 }
}

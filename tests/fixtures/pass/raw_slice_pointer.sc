let Slice = core.memory.Slice

let main: (): i32 = {
  let mut values: Array<i32><2> = [40, 1]
  do {
    let view: Borrow<mut><Slice<i32>> = borrow<mut>(values)
    let pointer = unsafe {
      raw_slice_ptr<mut>(view)
    }
    unsafe {
      *raw_offset(pointer, 1) = 2
    }
  }
  let view: Borrow<Slice<i32>> = borrow(values)
  let pointer = unsafe {
    raw_slice_ptr(view)
  }
  unsafe {
    *pointer + *raw_offset(pointer, 1)
  }
}

test("raw_slice_pointer.sc") {
  std.test.assert(main() == 42)
}

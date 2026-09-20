let Slice = core.memory.Slice

let main = (): i32 => {
  let values: Array<i32><1> = [42]
  let view: Borrow<Slice<i32>> = borrow(values)
  let pointer = raw_slice_ptr(view)
  0
}

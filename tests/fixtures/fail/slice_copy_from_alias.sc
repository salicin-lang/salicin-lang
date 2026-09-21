let Slice = core.memory.Slice

let main: (): i32 = {
  let mut values: Array<i32><3> = [1, 2, 3]
  let destination: Borrow<mut><Slice<i32>> = borrow<mut>(values)
  let source: Borrow<Slice<i32>> = borrow(values)
  destination.copy_from(source)
  0
}

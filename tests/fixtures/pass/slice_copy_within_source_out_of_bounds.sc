let Slice = core.memory.Slice

let main(): i32 = {
  let mut values: Array<i32><3> = [1, 2, 3]
  let view: Borrow<mut><Slice<i32>> = borrow<mut>(values)
  view.copy_within(2, 1, 0)
  0
}

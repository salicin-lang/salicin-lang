let Slice = core.memory.Slice
let Vec = alloc.Vec

let main = {
  (): i32 =>
  let source: Array<i32><2> = [1, 2]
  let source_view: Borrow<Slice<i32>> = borrow(source)
  let mut values: Vec<i32> = Vec<i32>.new()
  values.push(3)
  values.copy_from(source_view)
  0
}

let Slice = core.memory.Slice

let main = (): i32 => {
  let source: Array<i32><2> = [1, 2]
  let mut destination: Array<i32><3> = [3, 4, 5]
  let source_view: Borrow<Slice<i32>> = borrow(source)
  let destination_view: Borrow<mut><Slice<i32>> = borrow<mut>(destination)
  destination_view.copy_from(source_view)
  0
}

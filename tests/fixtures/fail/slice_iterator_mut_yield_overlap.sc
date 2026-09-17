let Slice = core.memory.Slice

let main(): i32 = {
  let mut values: Array<i32><2> = [40, 2]
  let slice: Borrow<mut><Slice<i32>> = borrow<mut>(values)
  let mut iterator = Slice.iter(mut)()
  let first = iterator.next()
  let second = iterator.next()
  match first
    { Some(value) -> value }
    { None -> 0 }
}

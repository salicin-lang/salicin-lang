let Slice = core.memory.Slice
let Vec = alloc.Vec

let main = {
  (): i32 =>
  let mut values = Vec.new<T: i32>()
  values.push(1)
  values.push(2)
  values[1] = 40
  let borrowed = borrow(values[1])
  let from_vec = borrowed
  let mut array = [1, 2]
  let slice: Borrow<mut><Slice<i32>> = borrow<mut>(array)
  slice[0] = 2
  from_vec + slice[0]
}

test("index_protocol_containers.sc") {
  std.test.assert(main() == 42)
}

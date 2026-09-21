let Vec = alloc.Vec

let bomb = struct {}

extend(bomb, Droppable) {
  let drop = {
    (self: Borrow<mut><self>)
    (): () =>
    unsafe {
      raw_trap()
    }
  }
}

let main = {
  (): i32 =>
  let mut values: Vec<bomb> = Vec<bomb>.new()
  values.push(bomb {})
  0
}

test("vec_zst_resource_drop_trap.sc") {
  std.test.assert(main() == 42)
}

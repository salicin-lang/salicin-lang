let select = trait {
  pick: (self: Borrow<self>)(left: i32): i32;
  pick: (self: Borrow<self>)(right: i32): i32;
  make: (left: i32): i32;
  make: (right: i32): i32
}

let counter = struct { value: i32 }

extend<counter, select> {
  let pick: (self: Borrow<self>)(left: i32): i32 = { self.value + left }
  let pick: (self: Borrow<self>)(right: i32): i32 = { self.value + right + 1 }
  let make: (left: i32): i32 = { left }
  let make: (right: i32): i32 = { right + 1 }
}

let main: (): i32 = {
  counter { value: 0 }.pick(right: 20) + counter.make(right: 20)
}

test<"trait_overload_named.sc"> {
  std.test.assert(main() == 42)
}

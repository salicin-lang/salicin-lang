let AddAssign = core.ops.AddAssign
let BitXorAssign = core.ops.BitXorAssign

let counter = struct { value: i32 }

extend<counter> {
  let add_assign(self: Borrow<self>)(rhs: i32): bool = { false }
}

extend<counter, AddAssign<i32>> {
  let add_assign(self: Borrow<mut><self>)
    (rhs: i32): () = {
    self.value += rhs
  }
}

extend<counter, BitXorAssign<i32>> {
  let bit_xor_assign(self: Borrow<mut><self>)
    (rhs: i32): () = {
    self.value ^= rhs
  }
}

let main(): i32 = {
  let mut counter = counter { value: 40 }
  counter += 2
  counter ^= 0
  counter.value
}

test<"compound_assign_trait.sc"> {
  std.test.assert(main() == 42)
}

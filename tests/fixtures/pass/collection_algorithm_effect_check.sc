let Option = core.Option
let Slice = core.memory.Slice
let inspect = effect {
  accepted: (value: i32): bool
}

let read = { (value: Borrow<i32>): i32 => value }

let effect_greater_than_ten = { with<inspect>
  (value: Borrow<i32>): bool =>
  inspect.accepted(read(value))
}

let locate = { with<inspect>(values: Borrow<Slice<i32>>): Option<u64> =>
  values.position(effect_greater_than_ten)
}

let main = { (): i32 => 42 }

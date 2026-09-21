let Slice = core.memory.Slice

let escape = {
  (): Borrow<i32> =>
  let values: Array<i32><1> = [42]
  let slice: Borrow<Slice<i32>> = borrow(values)
  let mut iterator = Slice.iter()
  match(iterator.next()) {
    Some(value) => value, None => do {
      unsafe { raw_trap() }
    },
  }
}

let main = { (): i32 => 0 }

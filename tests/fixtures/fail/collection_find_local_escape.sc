let Option = core.Option

let read = { (value: Borrow<i32>): i32 => value }
let positive = { (value: Borrow<i32>): bool => read(value) > 0 }

let invalid = { (): Option<Borrow<i32>> =>
  let values: Array<i32><1> = [42]
  values.find(positive)
}

let main = { (): i32 => invalid()!! }

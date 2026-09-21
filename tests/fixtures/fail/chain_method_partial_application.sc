let Option = core.Option

let number = struct { value: i32 }

extend(number) {
  let plus = { (self: Borrow<self>)(x: i32)(y: i32): i32 => self.value + x + y }
}

let main = {
  (): i32 =>
  let add_last = Option<number>.Some(number { value: 40 })?.plus(1)
  add_last(1) ?? 0
}

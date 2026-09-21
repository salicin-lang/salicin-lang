let convert = trait {
  Output: type
  convert: (self: Borrow<self>)(): Output
}

let number = struct { value: i32 }

extend<number, convert> {
  let Output = i32;
  let convert: (self: Borrow<self>)(): i32 = { self.value }}

let main: (): i32 = {
  let number = number { value: 42 }
  number.convert()
}

test<"trait_associated_output.sc"> {
  std.test.assert(main() == 42)
}

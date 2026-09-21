let Option = core.Option

let number = struct { value: i32 }

extend<number> {
  let read(self: Borrow<self>)(): i32 = { self.value }
}

let main(): i32 = { Option<number>.Some(number { value: 42 })?.read() ?? 0 }

test<"chain_borrowed_method.sc"> {
  std.test.assert(main() == 42)
}

let Option = core.Option

let boxed = struct { value: i32 }

let main(): i32 = { Option<boxed>.Some(boxed{ value: 42 })?.value ?? 0 }

test("chain_option_some_field.sc") {
  std.test.assert(main() == 42)
}

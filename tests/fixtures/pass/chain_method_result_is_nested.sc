let Option = core.Option

let boxed = struct { value: i32 }

extend(boxed) {
  let optional = { (move self)(): Option<i32> => Option<i32>.Some(self.value) }
}

let main = { (): i32 =>
  let nested = Option<boxed>.Some(boxed { value: 42 })?.optional()
  match(nested) { Some(inner) => inner ?? 0, None => 0,
  }
}

test("chain_method_result_is_nested.sc") {
  std.test.assert(main() == 42)
}

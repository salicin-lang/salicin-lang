let Result = core.Result

let number = struct { value: i32 }

extend(number) {
  let take(move self)(): i32 = { self.value }
}

let main(): i32 = { Result<bool><number>.Ok(number{ value: 42 })?.take() ?? 0 }

test("chain_result_method.sc") {
  std.test.assert(main() == 42)
}

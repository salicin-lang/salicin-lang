let Option = core.Option
let Result = core.Result

let boxed = struct { value: i32 }

let option_value = { (): Option<i32> => Option.Some(boxed { value: 20 })?.value }

let result_value = { (): Result<bool><i32> => Result.Ok(boxed { value: 22 })?.value }

let main = { (): i32 => (option_value() ?? 0) + (result_value() ?? 0) }

test("chain_inferred_inputs.sc") {
  std.test.assert(main() == 42)
}

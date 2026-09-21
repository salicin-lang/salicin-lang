let Result = core.Result

let boxed = struct { value: i32 }

let main: (): i32 = { Result<bool><boxed>.Ok(boxed { value: 42 })?.value ?? 0 }

test("chain_result_ok_field.sc") {
  std.test.assert(main() == 42)
}

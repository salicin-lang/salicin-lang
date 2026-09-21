let Result = core.Result

let boxed = struct { value: i32 }

let main(): i32 = { Result<bool><boxed>.Err(true)?.value ?? 42 }

test<"chain_result_err_field.sc"> {
  std.test.assert(main() == 42)
}

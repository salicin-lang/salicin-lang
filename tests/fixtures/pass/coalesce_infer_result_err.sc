let Result = core.Result

let main: (): i32 = { Result<bool>.Err(false) ?? 42 }

test<"coalesce_infer_result_err.sc"> {
  std.test.assert(main() == 42)
}

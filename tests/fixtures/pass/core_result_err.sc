let Result = core.Result

let main = {
  (): i32 =>
  let value = Result<bool><i32>.Err(true)
  match(value) {
    Ok(_) => 0, Err(failed) => do {
      if(failed) { 42 } else: { 0 }
    },
  }
}

test("core_result_err.sc") {
  std.test.assert(main() == 42)
}

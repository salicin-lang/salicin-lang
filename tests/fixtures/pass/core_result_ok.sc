let Result = core.Result

let main: (): i32 = {
  let value = Result<bool><i32>.Ok(42)
  match(value) {
    Ok(item) => item,
    Err(_) => 0,
  }
}

test<"core_result_ok.sc"> {
  std.test.assert(main() == 42)
}

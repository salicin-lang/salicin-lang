let Option = core.Option
let Result = core.Result

let main = (): i32 => {
  let inner = Result<bool><i32>.Ok(42)
  let outer = Option<Result<bool><i32>>.Some(inner)
  match(outer) {
    Some(result) => do {
      match(result) { Ok(value) => value, Err(_) => 0,
      }
    }, None => 0,
  }
}

test("core_nested_option_result.sc") {
  std.test.assert(main() == 42)
}

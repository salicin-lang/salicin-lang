let Option = core.Option
let Result = core.Result

let main(): i32 = {
  let option = Option.Some(20)
  let result = Result<bool>.Ok(22)
  option!! + result!!
}

test("unwrap_option_result.sc") {
  std.test.assert(main() == 42)
}

let Result = core.Result

let boxed = struct { answer: bool }

let main(): i32 = {
  let answer = Result<bool><boxed>.Ok(boxed{ answer: true })?.answer
  if(answer ?? false) { 42 } else: { 0 }
}

test("chain_success_type_changes.sc") {
  std.test.assert(main() == 42)
}

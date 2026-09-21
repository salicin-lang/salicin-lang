let Result = core.Result
let throwing = core.error.throwing

let extract: with<throwing<bool>>(move result: Result<bool><i32>): i32 = {
  result!
}

let main: (): i32 = {
  let success = try {
    extract(Result.Ok(42))
  }!!
  let failure = try {
    extract(Result.Err(false))
  } ?? 0
  success + failure
}

test("raise_result.sc") {
  std.test.assert(main() == 42)
}

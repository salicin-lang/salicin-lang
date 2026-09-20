let Result = core.Result
let throwing = core.error.throwing

let choose = { with<throwing<bool>>(flag: bool): i32 =>
  if(flag) {
    throw(true)
  } else: {
    42
  }
}

let main = { (): i32 =>
  let first: Result<bool><i32> = try { choose(false) }
  let second: Result<bool><i32> = try { choose(true) }
  (first ?? 0) + (second ?? 0)
}

test("throw_if_flow.sc") {
  std.test.assert(main() == 42)
}

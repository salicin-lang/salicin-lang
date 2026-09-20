let Result = core.Result
let throwing = core.error.throwing
let defer = core.control.defer

let fail = { with<throwing<bool>>(counter: Borrow<mut><i32>): i32 =>
  defer {
    counter = counter + 1
  }
  throw(true)
}

let main = { (): i32 =>
  let mut counter = 0
  let result: Result<bool><i32> = try {
    fail(counter)
  }
  match(result) {
    Ok(_) => 0, Err(error) => do {
      if(error && counter == 1) { 42 } else: { 0 }
    },
  }
}

test("defer_throw.sc") {
  std.test.assert(main() == 42)
}

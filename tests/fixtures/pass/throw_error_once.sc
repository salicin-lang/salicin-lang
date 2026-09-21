let Result = core.Result
let throwing = core.error.throwing

let make_error: (count: Borrow<mut><i32>): bool = {
  count = count + 1
  true
}

let fail: with<throwing<bool>>(): i32 = {
  let mut count = 0
  throw(make_error(count))
}

let main: (): i32 = {
  let result: Result<bool><i32> = try { fail() }
  match(result) {
    Ok(_) => 0,
    Err(error) => do {
      if(error) { 42 } else: { 0 }
    },
  }
}

test<"throw_error_once.sc"> {
  std.test.assert(main() == 42)
}

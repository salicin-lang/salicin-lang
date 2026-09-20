let Result = core.Result
let throwing = core.error.throwing
let Raise = core.flow.Raise

let stored = enum {
  value(i32),
  failure(bool),
}

extend(stored, Raise) {
  let Output = i32;
  let Error = bool;

  let raise = { with<throwing<bool>>(move self): i32 =>
    match(self) { value(value) => value, failure(error) => throw(error),
    }
  }
}

let extract = { with<throwing<bool>>(move stored: stored): i32 =>
  stored!
}

let extract_direct = { with<throwing<bool>>(move stored: stored): i32 =>
  stored.raise()
}

let extract_local = { with<throwing<bool>>(): i32 =>
  let stored: stored = stored.value(42)
  stored.raise()
}

let main = { (): i32 =>
  let success = try {
    extract_local()
  }!!
  let failure = try {
    extract(stored.failure(false))
  } ?? 0
  success + failure
}

test("raise_custom.sc") {
  std.test.assert(main() == 42)
}

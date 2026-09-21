let apply: (value: i32)
  {transform: (i32): i32}
  : i32 = { transform(value) }

let Point = struct {
  value: i32,
}

let evaluate: {action: (): i32}: i32 = { action() }

let main: (): i32 = {
  let value = evaluate {
    42
  }
  let point = Point {
    value: apply(value - 1) {
      (item) => item + 1
    },
  }
  point.value
}

test("unified_callable_brace_call.sc") {
  std.test.assert(main() == 42)
}

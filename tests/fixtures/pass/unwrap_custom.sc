let Unwrap = core.flow.Unwrap

let present = enum {
  value(i32),
}

extend(present, Unwrap) {
  let Output = i32;

  let unwrap: (move self): i32 = {
    match(self) {
      value(value) => value,
    }
  }
}

let main: (): i32 = { present.value(42)!! }

test("unwrap_custom.sc") {
  std.test.assert(main() == 42)
}

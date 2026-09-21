let Option = core.Option

let payload = struct {
  value: i32,
}

let main = {
  (): i32 =>
  let offset = 1
  let choose: (Option<payload>): core.control.Attempt<Option<payload>><i32> = {
    partial Some(payload) if payload.value > 100 => payload.value + offset
  }
  let attempted = choose(Option.Some(payload { value: 42 }))
  match(attempted) {
    Hit(_) => 0, Miss(remaining) => do {
      match(remaining) { Some(payload) => payload.value, None => 0,
      }
    },
  }
}

test("pattern_partial_guard_miss.sc") {
  std.test.assert(main() == 42)
}

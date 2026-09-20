let decide = effect {
  accept (value: i32): bool
}

let event = enum { value { value: i32 }, Empty }

extend(event, Copyable) {}

let accepted = { with<decide>(value: i32): bool =>
  decide.accept(value)
}

let classify_direct = { with<decide>(event: event): i32 =>
  match(event) { value( value: value ) if decide.accept(value) => value, value( value: value ) => value + 1, Empty => 0,
  }
}

let classify_named = { with<decide>(event: event): i32 =>
  match(event) { value( value: value ) if accepted(value) => value, value( value: value ) => value + 1, Empty => 0,
  }
}

let main = { (): i32 =>
  decide.handle(do {
      classify_direct(event.value { value: 20 }) + classify_named(event.value { value: 21 })
    }) {
    accept(value, resume) => do { resume(value == 20) },
  }
}

test("algebraic_effect_match_guard.sc") {
  std.test.assert(main() == 42)
}

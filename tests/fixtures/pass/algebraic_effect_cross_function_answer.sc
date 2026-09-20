let decide = effect {
  choose: (): bool
}

let choose_value = { with<decide>(): bool =>
  decide.choose()
}

let main = { (): i32 =>
  decide.handle(do {
      if(choose_value()) { 42 } else: { 0 }
    }) {
    choose(resume) => do { resume(true) },
  }
}

test("algebraic_effect_cross_function_answer.sc") {
  std.test.assert(main() == 42)
}

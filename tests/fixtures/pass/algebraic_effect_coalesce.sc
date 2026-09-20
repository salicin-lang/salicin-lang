let Option = core.Option
let Result = core.Result

let query = effect {
  let option = (present: bool): Option<bool>;
  let result = (present: bool): Result<()><bool>;
  let fallback = (): bool
}

let program = with<query>(): i32 => {
  let option_some = if(query.option(true) ?? query.fallback()) { 10 } else: { 0 }
  let option_none = if(query.option(false) ?? query.fallback()) { 10 } else: { 0 }
  let result_ok = if(query.result(true) ?? query.fallback()) { 10 } else: { 0 }
  let result_err = if(query.result(false) ?? query.fallback()) { 10 } else: { 0 }
  option_some + option_none + result_ok + result_err
}

let main = (): i32 => {
  let mut fallbacks = 0
  let result = query.handle {
    option: (present, resume) => {
      resume(if(present) { Option.Some(true) } else: { Option.None })
    },
    result: (present, resume) => {
      resume(if(present) { Result.Ok(true) } else: { Result.Err(()) })
    },
    fallback: (resume) => {
      fallbacks += 1;
      resume(true)
    },
    action: {
      program()
    },
  }
  result + fallbacks
}

test("algebraic_effect_coalesce.sc") {
  std.test.assert(main() == 42)
}

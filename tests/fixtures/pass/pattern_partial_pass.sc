let Option = core.Option

let apply = { (
    move choose: (Option<i32>): core.control.Attempt<Option<i32>><i32>,
  )(move input: Option<i32>): core.control.Attempt<Option<i32>><i32> =>
  choose(input)
}

let main = {
  (): i32 =>
  let attempted = apply({ partial Some(value) => value })(Option.Some(42))
  match(attempted) { Hit(value) => value, Miss(_) => 0,
  }
}

test("pattern_partial_pass.sc") {
  std.test.assert(main() == 42)
}

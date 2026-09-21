let Option = core.Option

let main: (): i32 = {
  let first = Option<i32>.None
  let second = Option<i32>.Some(42)
  first ?? second ?? 0
}

test<"coalesce_right_associative.sc"> {
  std.test.assert(main() == 42)
}

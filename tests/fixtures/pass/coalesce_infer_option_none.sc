let Option = core.Option

let main: (): i32 = { Option.None ?? 42 }

test("coalesce_infer_option_none.sc") {
  std.test.assert(main() == 42)
}

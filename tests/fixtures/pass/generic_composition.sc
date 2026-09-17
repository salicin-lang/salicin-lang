let identity<t: type>(move value: t): t = { value }

let wrap<t: type>(move value: t): t = { identity<t>(value) }

let main(): i32 = { wrap<i32>(42) }

test("generic_composition.sc") {
  std.test.assert(main() == 42)
}

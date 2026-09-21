let Add = core.ops.Add

let twice: <t: type>
  (copy value: t): t
requires<t is Add<t> && t.Output == t && t is Copyable> = {
  let left = value
  let right = value
  left + right
}

let main: (): i32 = { twice(21) }

test<"where_operator_output.sc"> {
  std.test.assert(main() == 42)
}

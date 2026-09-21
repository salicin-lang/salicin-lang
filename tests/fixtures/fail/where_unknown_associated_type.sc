let marker = trait {}

let identity<t: type>(value: t): t
  requires<t is marker && t.Item == t> = { value }

let main(): i32 = { identity(42) }

let identity<t: type>(value: t): t
= requires(t is Copyable && t is Copyable) { value }

let main(): i32 = { identity(42) }

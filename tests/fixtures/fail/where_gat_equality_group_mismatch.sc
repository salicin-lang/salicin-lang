let lend = trait {
  let Item<a: access><r: region>: type
}

let require<t: type>(move value: t): ()
= requires(t is lend && t.Item<a: access, r: region> == borrow(a)<r>(i32)) {}

let main(): () = {}

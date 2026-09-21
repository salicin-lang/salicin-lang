let view<t: type><r: region>: type = Borrow<r><t>

let lend = trait {
  Item<r: region>: type
}

let cell = struct { value: i32 }

extend<cell, lend> {
  let Item = view<i32>;
}

let require_i64<t: type>(move value: t): ()
  requires<t is lend && t.Item<r: region> == Borrow<r><i64>> = { }

let main(): () = {
  require_i64(cell { value: 42 })
}

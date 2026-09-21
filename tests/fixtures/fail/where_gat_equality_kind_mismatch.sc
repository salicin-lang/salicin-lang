let lend = trait {
  Item: <a: access>: type
}

let require: <t: type>(move value: t): ()
  requires(t is lend && t.Item<r: region> == i32) = { }

let main: (): () = { }

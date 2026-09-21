let pair: <k: type, v: type> = struct { key: k, value: v }

let pair_alias: <key: type, value: type>: type = pair

let holds: <item: type> = trait {
  get: (self: Borrow<self>)(): item
}

extend(pair<i32, bool>, holds<item: i32>) {
  let get: (self: Borrow<self>)(): i32 = { self.key }
}

let read: <t: type>
  (value: Borrow<t>): i32
requires(t is holds<item: i32>) = {
  value.get()
}

let make: (): pair_alias<value: bool, key: i32> = {
  pair<k: i32, v: bool> { key: 41, value: true }
}

let main: (): i32 = {
  let pair_value: pair<v: bool, k: i32> = make()
  if(pair_value.value) { read<t: pair<i32, bool>>(pair_value) + 1 } else: { 0 }
}

test("type_constructor_labeled_arguments.sc") {
  std.test.assert(main() == 42)
}

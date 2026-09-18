let produce = trait {
  let Item: type
  let produce(self: Borrow<self>)(): item
}

let value = struct { value: i32 }

extend(value, produce) {
  let Item = i32;
  let produce(self: Borrow<self>)(): i32 = { self.value }
}

let require_bool<t: type>(value: Borrow<t>): bool
  = requires(t is produce && t.Item == bool) { value.produce() }

let main(): i32 = {
  let value = value{ value: 42 }
  if(require_bool(value)) { 42 } else: { 0 }
}

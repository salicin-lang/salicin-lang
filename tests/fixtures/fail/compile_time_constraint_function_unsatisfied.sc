let resource = struct { value: i32 }

let duplicate: <t: type> = { (copy value: t): (t, t) requires(t is Copyable) =>
  (value, value)
}

let main = {
  (): i32 =>
  let pair = duplicate(resource { value: 42 })
  pair.0.value
}

let resource = struct { value: i32 }

let duplicate = <t: type>(copy value: t): t
  requires(t is Copyable) => {
  let first = value
  value
}

let main = (): i32 => { duplicate(resource{ value: 42 }).value }

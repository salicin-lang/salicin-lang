let resource = struct { value: i32 }
let cell<t: type> = struct { value: t }

extend(cell<t>)<requires: t is Copyable> {
  let new(copy value: t): cell<t> = { cell{ value: value } }
}

let main(): i32 = { cell.new(resource{ value: 42 }).value.value }

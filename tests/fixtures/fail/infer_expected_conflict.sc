let cell<t: type> = struct { value: t }

let main(): i32 = {
  let cell: cell(bool) = cell{ value: 42 }
  42
}

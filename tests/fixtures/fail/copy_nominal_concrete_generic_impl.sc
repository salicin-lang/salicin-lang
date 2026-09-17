let cell<t: type> = struct { value: t }

extend(cell(i32), Copyable) {}

let read(copy cell: cell(i64)): i64 = { cell.value }

let main(): i32 = { 42 }

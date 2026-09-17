let Box = alloc.Box

let resource = struct { value: i32 }

let main(): i32 = { Box.new(resource{ value: 42 }).read().value }

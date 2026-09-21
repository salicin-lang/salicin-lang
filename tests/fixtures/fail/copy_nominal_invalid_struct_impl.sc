let payload = struct { value: i32 }

let container = struct { payload: payload }

extend<container, Copyable> {}

let main(): i32 = { 42 }

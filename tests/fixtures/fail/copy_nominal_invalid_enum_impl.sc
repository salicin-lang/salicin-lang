let payload = struct { value: i32 }

let message = enum {
  data(payload),
  Empty,
}

extend<message, Copyable> {}

let main: (): i32 = { 42 }

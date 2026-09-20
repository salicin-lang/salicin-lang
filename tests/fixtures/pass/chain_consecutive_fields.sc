let Option = core.Option

let inner = struct { answer: i32 }
let middle = struct { inner: inner }
let outer = struct { middle: middle }

let main = (): i32 => {
  Option<outer>.Some(outer { middle: middle { inner: inner { answer: 42 } } })?.middle?.inner?.answer ?? 0
}

test("chain_consecutive_fields.sc") {
  std.test.assert(main() == 42)
}

let read = effect {
  read: (): usize
}

let main = { (): i32 =>
  read.handle(do {
      let values = [42, 0]
      match(values[read.read()]) {
        42 => do {
          if(read.read() == 0) { 42 } else: { 0 }
        }, _ => 0,
      }
    }) {
    read(resume) => do { resume(0) },
  }
}

test("algebraic_effect_expression_traversal.sc") {
  std.test.assert(main() == 42)
}

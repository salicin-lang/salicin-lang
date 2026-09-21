let read = effect {
  read: (): i32
}

let sum_reads = { with<read>
  (count: i32): i32 =>
  if(count == 0) {
    return(0)
  }
  read.read() + sum_reads(count - 1)
}

let main = {
  (): i32 =>
  let value = 14
  read.handle(do {
    sum_reads(3)
  }) {
    read(resume) => do { resume(value) },
  }
}

test("algebraic_effect_recursion.sc") {
  std.test.assert(main() == 42)
}

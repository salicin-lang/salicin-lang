let read = effect {
  read (): i32
}

let resource = struct { counter: Ptr<mut><i32> }

extend(resource, Droppable) {
  let drop = { (self: Borrow<mut><self>)(): () =>
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

let read_early = { with<read>(counter: Ptr<mut><i32>): i32 =>
  let resource = resource { counter: counter }
  let value = read.read()
  return(value)
}

let main = { (): i32 =>
  let counter = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe { *counter = 0 }
  let result = read.handle(do {
      read_early(counter)
    }) {
    read(resume) => do { resume(41) },
  }
  let drops = unsafe { *counter }
  unsafe {
    raw_dealloc(counter, size_of<i32>, align_of<i32>)
  }
  result + drops
}

test("algebraic_effect_explicit_return.sc") {
  std.test.assert(main() == 42)
}

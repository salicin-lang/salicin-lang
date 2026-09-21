let Vec = alloc.Vec

let resource = struct { counter: Ptr<mut><i32>, value: i32 }

extend(resource) {
  let read: (self: Borrow<self>)(): i32 = { self.value }
}

extend(resource, Droppable) {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.counter = *self.counter + 1
    }
  }
}

let main: (): i32 = {
  let counter = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    *counter = 0
  }
  let mut score = 0
  do {
    let mut values: Vec<resource> = Vec<resource>.new()
    let started_empty = values.is_empty()
    values.reserve(4)
    values.push(resource { counter: counter, value: 1 })
    values.push(resource { counter: counter, value: 2 })
    values.push(resource { counter: counter, value: 3 })
    values.push(resource { counter: counter, value: 4 })
    values.reserve(8)
    let before_remove = unsafe {
      *counter
    }
    let removed_value = do {
      let removed = values.swap_remove(1)
      removed.read()
    }
    values.truncate(2)
    values.truncate(9)
    let after_truncate = unsafe {
      *counter
    }
    values.clear()
    values.clear()
    let after_clear = unsafe {
      *counter
    }
    let ended_empty = values.is_empty()
    values.push(resource { counter: counter, value: 5 })
    if(started_empty && ended_empty && before_remove == 0 && removed_value == 2 && after_truncate == 2 && after_clear == 4) {
      score = 37
    }
  }
  let drops = unsafe {
    *counter
  }
  unsafe {
    raw_dealloc(counter, size_of<i32>, align_of<i32>)
  }
  if(drops == 5) {
    score + drops
  } else: {
    0
  }
}

test("vec_resource.sc") {
  std.test.assert(main() == 42)
}

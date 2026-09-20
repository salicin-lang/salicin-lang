let Slice = core.memory.Slice

let resource = struct {
  value: i32,
  drops: Ptr<mut><i32>,
}

extend(resource, Droppable) {
  let drop = (self: Borrow<mut><self>)(): () => {
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

let read = (value: Borrow<resource>): i32 => { value.value }

let main = (): i32 => {
  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe { *drops = 0 }

  let total = do {
    let values: Array<resource><3> = [
      resource { value: 9, drops: drops },
      resource { value: 12, drops: drops },
      resource { value: 21, drops: drops },
    ]
    let slice: Borrow<Slice<resource>> = borrow(values)
    let mut sum = 0
    for slice.iter() { value ->
      sum = sum + read(value)
    }
    sum
  }
  let drop_count = unsafe { *drops }
  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }
  total + drop_count - 3
}

test("slice_iterator_resource.sc") {
  std.test.assert(main() == 42)
}

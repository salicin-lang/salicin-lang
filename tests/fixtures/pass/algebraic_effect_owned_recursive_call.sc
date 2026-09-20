let step = effect {
  delta (): i32
}

let state = struct {
  value: i32,
  drops: Ptr<mut><i32>,
}

extend(state, Droppable) {
  let drop = { (self: Borrow<mut><self>)(): () =>
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

let walk = { with<step>(state: Borrow<mut><state>, count: i32): i32 =>
  if(count == 0) {
    return(state.value)
  }
  let delta = step.delta()
  state.value = state.value + delta
  let nested = walk(state, count - 1)
  nested + state.value
}

let run = { (drops: Ptr<mut><i32>, abandon: bool): i32 =>
  let mut state = state { value: 18, drops: drops }
  let result = step.handle(do {
      walk(state, 2)
    }) {
    delta(resume) => do {
      if(abandon) { 40 } else: { resume(1) }
    },
  }
  result + state.value
}

let main = { (): i32 =>
  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe { *drops = 0 }

  let resumed = run(drops, false)
  let abandoned = run(drops, true)
  let drop_count = unsafe { *drops }

  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }
  resumed + abandoned + drop_count - 98
}

test("algebraic_effect_owned_recursive_call.sc") {
  std.test.assert(main() == 42)
}

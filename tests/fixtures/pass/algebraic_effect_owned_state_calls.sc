let step = effect {
  delta: (): i32
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

let update = { with<step>(state: Borrow<mut><state>): () =>
  let delta = step.delta()
  state.value = state.value + delta
}

let program = { with<step>(drops: Ptr<mut><i32>): i32 =>
  let mut state = state { value: 40, drops: drops }
  update(state)
  update(state)
  state.value
}

let main = { (): i32 =>
  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe { *drops = 0 }

  let resumed = step.handle(do {
      program(drops)
    }) {
    delta(resume) => do {
      resume(1)
    },
  }
  let abandoned = step.handle(do {
      program(drops)
    }) {
    delta(_) => do {
      40
    },
  }
  let drop_count = unsafe { *drops }

  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }
  resumed + abandoned + drop_count - 42
}

test("algebraic_effect_owned_state_calls.sc") {
  std.test.assert(main() == 42)
}

let Result = core.Result
let throwing = core.error.throwing

let step = effect {
  delta(): i32
}

let state = struct {
  value: i32,
  drops: Ptr<mut><i32>,
}

extend<state, Droppable> {
  let drop(self: Borrow<mut><self>)
    (): () = {
    unsafe {
      *self.drops = *self.drops + 1
    }
  }
}

let accept with<throwing<bool>>(fail: bool): i32 = {
  if(fail) { throw(true) } else: { 0 }
}

let update with<step, throwing<bool>>(state: Borrow<mut><state>, fail: bool): i32 = {
  let accepted = accept(fail)
  let delta = step.delta()
  state.value = state.value + delta
  state.value + accepted
}

let run with<throwing<bool>>(drops: Ptr<mut><i32>, fail: bool): i32 = {
  let mut state = state { value: 20, drops: drops }
  step.handle {
    delta: { (resume) => resume(1) },
    action: { update(state, fail) },
  }
}

let main(): i32 = {
  let drops = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe { *drops = 0 }

  let success: Result<bool><i32> = try { run(drops, false) }
  let failure: Result<bool><i32> = try { run(drops, true) }
  let drop_count = unsafe { *drops }

  unsafe {
    raw_dealloc(drops, size_of<i32>, align_of<i32>)
  }
  (success ?? 0) + (failure ?? 5) + drop_count + 14
}

test<"algebraic_effect_owned_residual_failure_outer.sc"> {
  std.test.assert(main() == 42)
}

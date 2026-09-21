let step = effect {
  tick: (): ()
}

let pair = struct { left: i32, right: i32 }

let update = { with<step>
  (left: Borrow<mut><i32>, right: Borrow<mut><i32>): () =>
  step.tick()
  left = left + 1
  right = right + 1
}

let main = {
  (): i32 =>
  let mut pair = pair { left: 20, right: 20 }
  step.handle(do {
    update(pair.left, pair.left)
    pair.left
  }) {
    tick(resume) => do {
      resume(())
    },
  }
}

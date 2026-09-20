let step = effect {
  tick (): ()
}

let pair = struct { left: i32, right: i32 }

let update = { with<step>(pair: Borrow<mut><pair>, left: Borrow<mut><i32>): () =>
  step.tick()
  pair.right = pair.right + 1
  left = left + 1
}

let main = { (): i32 =>
  let mut pair = pair{ left: 20, right: 20 }
  step.handle(do {
      update(pair, pair.left)
      pair.left + pair.right
    }) {
    tick(resume) => do {
      resume(())
    },
  }
}

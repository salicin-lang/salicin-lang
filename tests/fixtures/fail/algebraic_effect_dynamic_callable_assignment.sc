let ask = effect {
  value: (): i32
}

let left = { with<ask>(): i32 => ask.value() }
let middle = { with<ask>(): i32 => ask.value() + 1 }
let right = { with<ask>(): i32 => ask.value() + 2 }

let main = { (): i32 =>
  ask.handle(do {
      let first: with<ask>(): i32  = if(true) { left } else: { right }
      let second: with<ask>(): i32  = if(true) { left } else: { middle }
      let mut selected = first
      selected = second
      selected()
    }) {
    value(resume) => do { resume(40) },
  }
}

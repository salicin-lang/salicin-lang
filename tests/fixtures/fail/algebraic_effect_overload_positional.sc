let ask = effect {
  value: (left: i32): i32
  value: (right: i32): i32
}

let choose: with<ask>
  (): i32 = {
  ask.value(42)
}

let main: (): i32 = { 0 }

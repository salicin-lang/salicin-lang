let Slice = core.memory.Slice

let invalid: (): Borrow<Slice<i32>> = {
  let values = [20, 22]
  borrow(values)
}

let main: (): i32 = { invalid().at(0) }

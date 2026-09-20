let pair = struct { value: i32 }
let value = { <r: region>(pair: Borrow<r><pair>): Borrow<r><i32> => borrow(pair.value) }

let main = { (): i32 =>
  let mut pair = pair{ value: 42 }
  let reference = value(pair)
  pair.value = 0
  reference
}

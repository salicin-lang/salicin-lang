let bad = { <r: region>
  (seed: Borrow<r><i32>): Borrow<r><i32> =>
  let local = seed
  borrow(local)
}

let main = { (): i32 => 42 }

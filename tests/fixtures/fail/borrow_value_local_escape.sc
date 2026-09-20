let pass = <r: region>(value: Borrow<r><i32>): Borrow<r><i32> => { value }

let bad = <r: region>(seed: Borrow<r><i32>): Borrow<r><i32> => {
  let local = seed
  let reference: Borrow<i32> = borrow(local)
  pass(reference)
}

let main = (): i32 => { 42 }

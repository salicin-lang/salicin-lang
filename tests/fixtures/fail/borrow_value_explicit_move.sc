let consume(move value: Borrow<i32>): i32 = { value }

let main(): i32 = {
  let number = 21
  let reference: Borrow<i32> = borrow(number)
  let first = consume(reference)
  first + reference
}

let add(value: Borrow<i32>)(amount: i32): i32 = { value + amount }

let main(): i32 = {
  let number = 41
  let reference: Borrow<i32> = borrow(number)
  let pending = add(reference)
  pending(1)
}

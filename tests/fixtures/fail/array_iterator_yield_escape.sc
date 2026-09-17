let escape(): Borrow<i32> = {
  let values: Array<i32><1> = [42]
  let mut iterator = values.iter()
  match iterator.next()
    { Some(value) -> value }
    { None -> unsafe { raw_trap() } }
}

let main(): i32 = { 0 }

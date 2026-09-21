let Vec = alloc.Vec

let main = {
  (): i32 =>
  let mut values: Vec<i32> = Vec<i32>.new()
  values.push(1)
  values.push(2)
  values.copy_within(0, 2, 1)
  0
}

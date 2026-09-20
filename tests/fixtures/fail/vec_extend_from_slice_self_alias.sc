let Vec = alloc.Vec

let main = { (): i32 =>
  let mut values: Vec<i32> = Vec<i32>.new()
  values.push(42)
  let source = values.as_slice()
  values.extend_from_slice(source)
  0
}

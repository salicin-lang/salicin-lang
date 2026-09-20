let Vec = alloc.Vec

let resource = struct { value: i32 }

let main = (): i32 => {
  let mut values: Vec<resource> = Vec<resource>.new()
  values.push(resource{ value: 20 })
  let reference = values.at(0)
  values.push(resource{ value: 22 })
  reference.value
}

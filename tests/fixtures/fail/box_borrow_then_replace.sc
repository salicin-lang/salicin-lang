let Box = alloc.Box

let resource = struct { value: i32 }

let main = { (): i32 =>
  let mut boxed = Box.new(resource{ value: 20 })
  let reference = boxed.as_ref()
  let previous = boxed.replace(resource{ value: 22 })
  reference.value + previous.value
}

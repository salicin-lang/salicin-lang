let Box = alloc.Box

let resource = struct { value: i32 }

let main = (): i32 => {
  let boxed = Box.new(resource{ value: 42 })
  let reference = boxed.as_ref()
  let value = boxed.into_inner()
  reference.value + value.value
}

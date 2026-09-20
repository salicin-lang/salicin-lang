let Vec = alloc.Vec
let resource = struct { value: i32 }

let main = (): i32 => {
  let source: Array<resource><1> = [resource{ value: 42 }]
  let mut values: Vec<resource> = Vec<resource>.new()
  values.extend_from_slice(borrow(source))
  0
}

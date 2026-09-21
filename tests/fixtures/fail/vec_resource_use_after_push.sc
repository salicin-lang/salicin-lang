let Vec = alloc.Vec

let resource = struct { value: i32 }

extend<resource, Droppable> {
  let drop(self: Borrow<mut><self>)(): () = { }}

let main(): i32 = {
  let mut values: Vec<resource> = Vec<resource>.new()
  let resource = resource { value: 42 }
  values.push(resource)
  resource.value
}

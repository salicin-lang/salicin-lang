let resource = struct { value: i32 }

extend(resource, Droppable) {
  let drop: (self: Borrow<mut><self>)(): () = { () }
}

let main: (): i32 = {
  let values: Array<resource><1> = [resource { value: 42 }]
  for values { value => () }
  42
}

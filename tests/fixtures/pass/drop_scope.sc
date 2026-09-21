let resource = struct { value: i32 }
let choice = enum {
  Some(resource),
  None,
}

extend<resource, Droppable> {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    let checked = 1 / self.value
    self.value = 0
  }
}

let consume: (move value: resource): () = { () }

let conditional: (flag: bool): () = {
  let value = resource { value: 1 }
  if(flag) { consume(value) }
}

let inspect: (move choice: choice): i32 = {
  match(choice) {
    Some(_) => 1,
    None => 0,
  }
}

let early: (): i32 = {
  let value = resource { value: 1 }
  return(1)
}

let looped: (): i32 = {
  loop {
    let value = resource { value: 1 }
    break(1)
  }
}

let main: (): i32 = {
  do {
    let value = resource { value: 1 }
  }
  consume(resource { value: 1 })
  conditional(true)
  conditional(false)
  resource { value: 1 }
  let mut replaced = resource { value: 1 }
  replaced = resource { value: 1 }
  early() + looped() + inspect(choice.Some(resource { value: 1 })) + 39
}

test<"drop_scope.sc"> {
  std.test.assert(main() == 42)
}

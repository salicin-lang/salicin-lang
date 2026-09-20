let resource = struct { value: i32 }
let bundle = struct { left: resource, right: resource }
let choice = enum { Some(bundle, resource), None }

extend(resource, Droppable) {
  let drop = (self: Borrow<mut><self>)(): () => {
    let checked = 1 / self.value
    self.value = 0
  }
}

let consume = (move value: resource): () => { () }

let inspect = (move choice: choice): i32 => {
  match(choice) {
    Some(bundle(left: left, right: _), _) => do {
      do {
        consume(left)
        return(42)
      }
    }, None => 0,
  }
}

let main = (): i32 => { inspect(
    choice.Some(bundle { left: resource { value: 1 }, right: resource { value: 1 } }, resource { value: 1 })
  )
}

test("drop_match_nested.sc") {
  std.test.assert(main() == 42)
}

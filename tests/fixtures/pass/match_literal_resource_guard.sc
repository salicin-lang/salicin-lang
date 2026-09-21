let resource = struct { value: i32 }
let choice = enum { pair(resource, i32), None }

extend<resource, Droppable> {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    let checked = 1 / self.value
    self.value = 0
  }
}

let consume: (move value: resource): () = { () }

let choose: (move choice: choice): i32 = {
  match(choice) {
    pair(resource, 42) => do {
      do {
        consume(resource)
        21
      }
    },
    pair(resource, _) => do {
      do {
        consume(resource)
        21
      }
    },
    None => 0,
  }
}

let main: (): i32 = {
  choose(choice.pair(resource { value: 1 }, 0)) +
    choose(choice.pair(resource { value: 1 }, 42))
}

test<"match_literal_resource_guard.sc"> {
  std.test.assert(main() == 42)
}

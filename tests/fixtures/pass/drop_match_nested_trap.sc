let resource = struct { value: i32 }
let bundle = struct { left: resource, right: resource }
let choice = enum { Some(bundle, resource), None }

extend<resource, Droppable> {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    let trapped = 1 / self.value
  }
}

let consume: (move value: resource): () = { () }

let main: (): i32 = { match(choice.Some(
      bundle { left: resource { value: 1 }, right: resource { value: 0 } },
      resource { value: 1 }
  )) {
    Some(bundle(left: left, right: _), _) => do {
      do {
        consume(left)
        0
      }
    },
    None => 0,
  }
}

test<"drop_match_nested_trap.sc"> {
  std.test.assert(main() == 42)
}

let resource = struct { value: i32 }
let choice = enum { pair(resource, resource), None }

extend(resource, Droppable) {
  let drop: (self: Borrow<mut><self>)
    (): () = {
    let trapped = 1 / self.value
  }
}

let consume: (move value: resource): () = { () }

let main: (): i32 = {
  match(choice.pair(resource { value: 1 }, resource { value: 0 })) {
    pair(left, _) => do {
      do {
        consume(left)
        0
      }
    },
    None => 0,
  }
}

test("drop_match_payload_trap.sc") {
  std.test.assert(main() == 42)
}

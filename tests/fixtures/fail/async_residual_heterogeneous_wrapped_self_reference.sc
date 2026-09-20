let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  ask (): i32
}

let first = struct {
  value: i32,
}

let second = struct {
  value: i32,
}

extend(first, Future<()>) {
  let Output = i32;

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> =>
    Poll<i32>.Ready(self.value)
  }
}

extend(second, Future<()>) {
  let Output = i32;

  let poll = { <r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> =>
    Poll<i32>.Ready(self.value)
  }
}

let main = { (): i32 =>
  let future = async {
    let value = 1
    let reference: Borrow<i32> = borrow(value)
    let awaited = await(if(true) {
      first{ value: ask.ask() }
    } else: {
      second{ value: ask.ask() }
    })
    reference + awaited
  }
  0
}

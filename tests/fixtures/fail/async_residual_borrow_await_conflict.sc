let Future = core.async.Future
let Poll = core.async.Poll

let ask = effect {
  ask: (): i32
}

let step = struct {
  value: i32,
}

extend(step, Future<()>) {
  let Output = i32;

  let poll: <r: region>(self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    Poll<i32>.Ready(self.value)
  }
}

let make_step: with<ask>
  (): step = {
  step { value: ask.ask() }
}

let program: (value: Borrow<mut><i32>): i32 = {
  let future = async {
    let amount = await(make_step())
    value = value + amount
    value
  }
  value = 0
  42
}

let main: (): i32 = {
  let mut value = 2
  program(value)
}

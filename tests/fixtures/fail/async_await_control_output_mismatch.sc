let Poll = core.async.Poll
let Future = core.async.Future

let number = struct {}
let flag = struct {}

extend(number, Future<()>) {
  let Output = i32;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<i32> = {
    Poll<i32>.Ready(42)
  }
}

extend(flag, Future<()>) {
  let Output = bool;

  let poll<r: region>
    (self: Borrow<mut><r><self>)
    (): Poll<bool> = {
    Poll<bool>.Ready(true)
  }
}

let main(): i32 = {
  let future = async {
    if true {
      await number{}
    } else {
      await flag{}
    }
  }
  0
}

let Result = core.Result
let throwing = core.error.throwing

let fail<error: type>: with<throwing<i32>, throwing<bool>>(move error: error): never = {
  throw(error)
}

let main(): i32 = {
  0
}

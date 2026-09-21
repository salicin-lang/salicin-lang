let Result = core.Result
let throwing = core.error.throwing

let fail: with<throwing<bool>>(): i32 = {
  throw
}

let main: (): i32 = { 42 }

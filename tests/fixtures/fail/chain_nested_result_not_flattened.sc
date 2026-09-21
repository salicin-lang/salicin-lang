let Result = core.Result

let boxed = struct { value: i32 }

extend<boxed> {
  let checked(move self)(): Result<bool><i32> = { Result<bool><i32>.Ok(self.value) }
}

let main(): i32 = {
  let flattened: Result<bool><i32> =
    Result<bool><boxed>.Ok(boxed { value: 42 })?.checked()
  flattened ?? 0
}

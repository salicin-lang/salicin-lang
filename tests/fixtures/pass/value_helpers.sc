let Option = core.Option
let Result = core.Result
let unsafety = core.unsafe.unsafety

let add_one(value: i32): i32 = { value + 1 }
let keep(value: i32): Option<i32> = { Option.Some(value) }
let keep_result(value: i32): Result<bool><i32> = { Result.Ok(value) }
let map_error(value: bool): i32 = {
  if value { 1 } else { 0 }
}
let option_fallback(): i32 = { 10 }
let result_fallback(error: bool): i32 = {
  if error { 11 } else { 10 }
}
let make_error(): bool = { true }
let impossible: with<unsafety>(): i32 = {
  unsafe { raw_trap() }
}
let impossible_error: with<unsafety>(error: bool): i32 = {
  unsafe { raw_trap() }
}

let main(): i32 = { unsafe {
  let mut maybe = Option.Some(1)
  let mut outcome: Result<bool><i32> = Result.Ok(2)

  do {
    let view = maybe.as_ref<mut>()
    match view
      { Some(value) -> value = value + 1 }
      { None -> () }
  }
  do {
    let view = outcome.as_ref<mut>()
    match view
      { Ok(value) -> value = value + 1 }
      { Err(_) -> () }
  }

  let borrowed =
    match maybe.as_ref()
      { Some(value) -> value }
      { None -> 0 } +
    match outcome.as_ref()
      { Ok(value) -> value }
      { Err(_) -> 0 }
  let states =
    if maybe.is_some() && !maybe.is_none() &&
       outcome.is_ok() && !outcome.is_err() { 1 } else { 0 }
  let mapped = Option.Some(1).map(add_one).and_then(keep).unwrap_or(0)
  let mapped_result =
    Result<bool><i32>.Ok(2).map(add_one).and_then(keep_result).unwrap_or(0)
  let mapped_error =
    Result<bool><i32>.Err(true).map_error(map_error).err().unwrap_or(0)
  let eager_option = Option.Some(4).unwrap_or_else(impossible)
  let eager_result = Result<bool><i32>.Ok(5).unwrap_or_else(impossible_error)
  let lazy_option = Option<i32>.None.unwrap_or_else(option_fallback)
  let lazy_result = Result<bool><i32>.Err(false).unwrap_or_else(result_fallback)
  let eager_error = Option<i32>.None.ok_or(true).err().unwrap_or(false)
  let lazy_error = Option<i32>.None.ok_or_else(make_error).err().unwrap_or(false)
  let success = Result<bool><i32>.Ok(6).ok().unwrap_or(0)
  let error = Result<bool><i32>.Err(true).err().unwrap_or(false)

  borrowed + states + mapped + mapped_result + mapped_error +
    eager_option + eager_result + lazy_option + lazy_result +
    (if eager_error { 1 } else { 0 }) +
    (if lazy_error { 1 } else { 0 }) +
    success + (if error { 1 } else { 0 }) - 8
} }

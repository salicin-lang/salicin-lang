let callee(
  move first: (bool): core.control.Attempt<bool><i32>,
): i32 = {
  42
}

let main(): i32 = {
  callee { true -> 1 } { false -> 2 }
}

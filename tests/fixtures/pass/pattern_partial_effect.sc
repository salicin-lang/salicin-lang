let unsafety = core.unsafe.unsafety

let dangerous = with<unsafety>(): i32 => {
  42
}

let main = (): i32 => {
  let choose: with<unsafety>(bool): core.control.Attempt<bool><i32>  = {
    true -> dangerous()
  }
  let attempted = unsafe {
    choose(true)
  }
  match(attempted) { Hit(value) => value, Miss(_) => 0,
  }
}

test("pattern_partial_effect.sc") {
  std.test.assert(main() == 42)
}

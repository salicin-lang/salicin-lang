let Option = core.Option
let unsafety = core.unsafe.unsafety
let Applicative = std.functional.Applicative
let Functor = std.functional.Functor
let Monad = std.functional.Monad

let unsafe_add_one: with<unsafety>
  (value: i32): i32 = {
  value + 1
}

let unsafe_next: with<unsafety>
  (value: i32): Option<i32> = {
  Option<i32>.Some(value + 2)
}

let read_option: (value: Option<i32>): i32 = {
  match(value) {
    Some(number) => number,
    None => 0,
  }
}

let main: (): i32 = {
  let mapped = unsafe {
    Option<i32>.Some(40).map(unsafe_add_one)
  }
  let applied = unsafe {
    let transform: Option<with<unsafety>(i32): i32>  = Option.Some(unsafe_add_one)
    transform.apply(Option<i32>.Some(1))
  }
  let chained = unsafe {
    Option<i32>.Some(40).flat_map(unsafe_next)
  }
  read_option(mapped) + read_option(applied) + read_option(chained) - 43
}

test<"functional_effect_forwarding.sc"> {
  std.test.assert(main() == 42)
}

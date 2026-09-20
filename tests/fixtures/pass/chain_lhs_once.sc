let Option = core.Option

let boxed = struct { value: i32 }

let make = (count: Borrow<mut><i32>): Option<boxed> => {
  count = count + 1
  Option<boxed>.Some(boxed { value: 42 })
}

let main = (): i32 => {
  let mut count = 0
  let answer = make(count)?.value ?? 0
  if(count == 1) { answer } else: { 0 }
}

test("chain_lhs_once.sc") {
  std.test.assert(main() == 42)
}

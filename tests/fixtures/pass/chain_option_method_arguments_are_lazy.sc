let Option = core.Option

let adder = struct { base: i32 }

extend(adder) {
  let add(self)(value: i32): i32 = { self.base + value }
}

let side_effect(count: Borrow<mut><i32>): i32 = {
  count = count + 1
  1
}

let main(): i32 = {
  let mut count = 0
  let answer = Option<adder>.None?.add(side_effect(count)) ?? 42
  if count == 0 { answer } else { 0 }
}

test("chain_option_method_arguments_are_lazy.sc") {
  std.test.assert(main() == 42)
}

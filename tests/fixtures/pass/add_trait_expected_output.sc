let Add = core.ops.Add

let number = struct { value: i32 }

extend(number, Add<i32>) {
  let Output = i32;
  let add = { (self)(rhs: i32): i32 => self.value + rhs }
}

extend(number, Add<i64>) {
  let Output = i64;
  let add = { (self)(rhs: i64): i64 => rhs + 40 }
}

let main = { (): i32 =>
  let answer: i64 = number { value: 40 } + 2
  if(answer == 42) { 42 } else: { 0 }
}

test("add_trait_expected_output.sc") {
  std.test.assert(main() == 42)
}

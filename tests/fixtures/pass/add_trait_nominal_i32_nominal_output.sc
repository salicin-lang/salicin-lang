let Add = core.ops.Add

let number = struct { value: i32 }

extend(number, Add<i32>) {
  let Output = number;
  let add = { (self)(rhs: i32): number => number { value: self.value + rhs } }
}

let main = {
  (): i32 =>
  let answer = number { value: 40 } + 2
  answer.value
}

test("add_trait_nominal_i32_nominal_output.sc") {
  std.test.assert(main() == 42)
}

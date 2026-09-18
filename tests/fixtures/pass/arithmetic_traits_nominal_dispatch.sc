let Sub = core.ops.Sub
let Mul = core.ops.Mul
let Div = core.ops.Div
let Rem = core.ops.Rem

let number = struct { value: i32 }

extend(number, Sub<number>) {
  let Output = number;
  let sub(self)(rhs: number): number = { number{ value: self.value - rhs.value } }
}

extend(number, Mul<number>) {
  let Output = number;
  let mul(self)(rhs: number): number = { number{ value: self.value * rhs.value } }
}

extend(number, Div<number>) {
  let Output = number;
  let div(self)(rhs: number): number = { number{ value: self.value / rhs.value } }
}

extend(number, Rem<number>) {
  let Output = number;
  let rem(self)(rhs: number): number = { number{ value: self.value % rhs.value } }
}

let main(): i32 = {
  let subtraction = number{ value: 50 } - number{ value: 8 }
  let multiplication = number{ value: 6 } * number{ value: 7 }
  let division = number{ value: 84 } / number{ value: 2 }
  let remainder = number{ value: 86 } % number{ value: 44 }
  if(subtraction.value == 42 && multiplication.value == 42 && division.value == 42 && remainder.value == 42) {
    42
  } else: {
    0
  }
}

test("arithmetic_traits_nominal_dispatch.sc") {
  std.test.assert(main() == 42)
}

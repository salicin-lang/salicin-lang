let Mul = core.ops.Mul

let number = struct { value: i32 }

extend(number, Mul<i32>) {
  let Output = i32;
  let mul(self)(rhs: i32): i32 = { self.value * rhs }
}

extend(number, Mul<i64>) {
  let Output = i64;
  let mul(self)(rhs: i64): i64 = { rhs * 21 }
}

let main(): i32 = {
  let answer = number{ value: 21 } * 2
  42
}

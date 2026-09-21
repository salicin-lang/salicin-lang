let Div = core.ops.Div

let number = struct { value: i32 }
let divisor = struct { value: i32 }

extend(number, Div<i32>) {
  let Output = i32;
  let div: (self)(rhs: i32): i32 = { self.value / rhs }
}

let main: (): i32 = { number { value: 84 } / divisor { value: 2 } }

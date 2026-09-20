let Add = core.ops.Add

let number = struct { value: i32 }

extend(number, Add<number>) {
  let Output = number;
  let add = (self)(rhs: number): number => { number{ value: self.value + rhs.value } }
}

let main = (): i32 => {
  let right = number{ value: 2 }
  let answer = number{ value: 40 } + right
  right.value
}

let Add = core.ops.Add

let number = struct { value: i32 }

extend(number, Add<number>) {
  let Output = number;
  let add: (self)(rhs: number): number = { number { value: self.value + rhs.value } }
}

let main: (): i32 = { 40 + 2 }

test("add_trait_builtin_integer_precedence.sc") {
  std.test.assert(main() == 42)
}

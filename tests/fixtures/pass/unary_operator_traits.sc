let Neg = core.ops.Neg
let Not = core.ops.Not

let number = struct { value: i32 }
let flag = struct { value: bool }

extend(number, Neg) {
  let Output = i32;
  let neg = { (self)(): i32 => -self.value }}

extend(flag, Not) {
  let Output = i32;
  let not = {
    (self)
    (): i32 =>
    if(self.value) { 0 } else: { 42 }
  }
}

let negate: <t: type> = { (move value: t): t requires(t is Neg && t.Output == t) => -value }
let invert: <t: type> = { (move value: t): t requires(t is Not && t.Output == t) => !value }

let main = {
  (): i32 =>
  if(invert(false)) {
    !flag { value: false } + -number { value: 0 } + negate(0)
  } else: {
    0
  }
}

test("unary_operator_traits.sc") {
  std.test.assert(main() == 42)
}

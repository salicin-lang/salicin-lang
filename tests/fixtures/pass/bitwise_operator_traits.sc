let BitAnd = core.ops.BitAnd
let BitOr = core.ops.BitOr
let BitXor = core.ops.BitXor
let Shl = core.ops.Shl
let Shr = core.ops.Shr

let bits = struct { value: i32 }

extend(bits, BitAnd<bits>) {
  let Output = bits;
  let bit_and(self)(rhs: bits): bits = { bits{ value: self.value & rhs.value } }
}
extend(bits, BitOr<bits>) {
  let Output = bits;
  let bit_or(self)(rhs: bits): bits = { bits{ value: self.value | rhs.value } }
}
extend(bits, BitXor<bits>) {
  let Output = bits;
  let bit_xor(self)(rhs: bits): bits = { bits{ value: self.value ^ rhs.value } }
}
extend(bits, Shl<bits>) {
  let Output = bits;
  let shl(self)(rhs: bits): bits = { bits{ value: self.value << rhs.value } }
}
extend(bits, Shr<bits>) {
  let Output = bits;
  let shr(self)(rhs: bits): bits = { bits{ value: self.value >> rhs.value } }
}

let mask<t: type>(move left: t)(move right: t): t
  = requires(t is BitAnd<t> && t.Output == t) { left & right }

let unsigned_shift(value: u32): u32 = { value >> 2 }

let main(): i32 = {
  let value = ((((mask(bits{ value: 6 })(bits{ value: 3 }) | bits{ value: 8 }) ^ bits{ value: 3 }) << bits{ value: 1 }) >> bits{ value: 1 }).value
  let builtins = (6 & 3) == 2 && (2 | 8) == 10 && (10 ^ 3) == 9 &&
    (9 << 1) == 18 && (-8 >> 2) == -2 && unsigned_shift(8) == 2
  if value == 9 && builtins { 42 } else { 0 }
}

test("bitwise_operator_traits.sc") {
  std.test.assert(main() == 42)
}

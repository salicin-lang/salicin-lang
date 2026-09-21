let Array = core.memory.Array
let Slice = core.memory.Slice

/// Constructs a value from the compiler's fixed-size backing Array for an
/// Array literal. Implementations may preserve the Array or build a user type.
pub let ArrayLiteral = <Element: type> trait {
  Output: type

  from_array_literal: <length: usize>
  (move values: Array<Element><length>): Output
}

/// Constructs a value from the UTF-8 backing bytes of a String literal.
pub let StringLiteral = trait {
  Output: type

  from_string_literal: <length: usize>
  (move utf8: Array<u8><length>): Output
}

/// The fixed-size Array implementation preserves the compiler backing value.
extend(Array<T><l>, ArrayLiteral<T>) {
  let Output = Array<T><l>;

  let from_array_literal = { <length: usize>
    (move values: Array<T><length>): Output => builtin() }
}

/// A UTF-8 byte Array can preserve String-literal backing without conversion.
extend(Array<u8><l>, StringLiteral) {
  let Output = Array<u8><l>;

  let from_string_literal = { <length: usize>
    (move utf8: Array<u8><length>): Output => builtin() }
}

/// A Slice literal is a Borrow of compiler-owned literal backing storage.
extend(Slice<T>, ArrayLiteral<T>) {
  let Output = Slice<T>;

  let from_array_literal = { <length: usize>
    (move values: Array<T><length>): Output => builtin() }
}

/// UTF-8 slices may be selected directly as the Result of a String literal.
extend(Slice<u8>, StringLiteral) {
  let Output = Slice<u8>;

  let from_string_literal = { <length: usize>
    (move utf8: Array<u8><length>): Output => builtin() }
}

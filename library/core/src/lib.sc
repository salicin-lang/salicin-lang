// Private bootstrap marker for declarations whose definitions are supplied by
// the compiler. Semantic validation gives each use its declaration annotation.
let builtin = { (): never => builtin() }

// Public syntax contracts. Their leading groups are erased metadata.
pub let abi = core.foreign.abi
pub let foreign = core.foreign.foreign
// Test names are consumed by the `test("...") { ... }` syntax. Each
// compiler-owned registration returns unit and may throw an owned message.
pub let test = { <name: String>{move body: with<core.error.throwing<core.string.String>>() :()}: () => builtin() }

// Function-definition guard. The parser supplies the normalized compile-time
// boolean and delays the guarded body as a parameterless closure.
pub let requires = { <
    condition: bool,
  e: effects,
  Result: type,
  >with<e>
  {move body: with<e>() :Result}: Result => builtin() }

pub let never = core.never.never
pub let Movable = core.marker.Movable
pub let Copyable = core.marker.Copyable
pub let Droppable = core.marker.Droppable
pub let bool = core.primitives.bool
pub let i8 = core.primitives.i8
pub let i16 = core.primitives.i16
pub let i32 = core.primitives.i32
pub let i64 = core.primitives.i64
pub let i128 = core.primitives.i128
pub let isize = core.primitives.isize
pub let u8 = core.primitives.u8
pub let u16 = core.primitives.u16
pub let u32 = core.primitives.u32
pub let u64 = core.primitives.u64
pub let u128 = core.primitives.u128
pub let usize = core.primitives.usize
pub let Option = core.option.Option
pub let Result = core.result.Result
pub let Array = core.memory.Array
pub let Slice = core.memory.Slice
pub let Ptr = core.memory.Ptr
pub let ptr = core.memory.ptr
pub let size_of = core.memory.size_of
pub let align_of = core.memory.align_of
pub let String = core.string.String
pub let str = core.string.str
pub let UnicodeScalar = core.string.UnicodeScalar
pub let ArrayLiteral = core.literal.ArrayLiteral
pub let StringLiteral = core.literal.StringLiteral

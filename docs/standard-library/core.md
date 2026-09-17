# Core library

`library/core` contains edition-matched declarations that do not require heap allocation or host
services. The compiler embeds these `.sc` files, parses them through the ordinary frontend, and
validates declarations that have language-defined roles.

Compiler-owned definitions are explicit. The private root declaration
`let builtin() = builtin()` bootstraps a declaration marker that is
unavailable to user packages. Primitive types, compiler-defined type
constructors, intrinsic functions, and intrinsic extension methods use
complete `= builtin()` initializers. Edition validation rejects missing,
unknown, malformed, or public markers. Trait requirements and effect
operations remain bodyless because they are abstract contracts, not
compiler-provided default implementations. Operations derivable from those
primitives remain ordinary Salicin definitions: the core implementation does
not use `builtin()` merely as an optimization annotation.

The same private root module declares
`pub let foreign<abi: abi>: never = builtin()`,
`pub let foreign<abi: abi, symbol: String>: never = builtin()`, and
`pub let test<name: String>(move body: with<core.error.throwing<core.string.String>>((): ())): () = builtin()`,
and the generic `requires(condition: bool, body)` function-body guard.
These are canonical syntax
contracts for foreign initializers and test registrations. `c` is the member of the finite
`abi` sort selected by `foreign(c)`; `test("name")` consumes its ordinary string
literal in syntax before lowering the structured-failure action.

## Modules

`core.lib` is the root facade. It only re-exports the public root surface: `never`, `Movable`, `Copyable`,
`Droppable`, `Option`, and `Result`.

`core.prelude` is also only a facade and contains the deliberately small implicit surface:

- the uninhabited `never` type
- the `Movable`, `Copyable`, and `Droppable` traits

The definitions live in focused modules. `core.never` owns `never`, `core.marker` owns `Movable`,
`Copyable`, and `Droppable`, and `core.option` and `core.result` own fundamental ordinary data types that are
intentionally not prelude names:

```sc fragment
pub let Option<T: type> = enum {
  Some(T),
  None,
}

pub let Result<E: type>
  <T: type> = enum {
  Ok(T),
  Err(E),
}
```

`core.testing` owns the normalized `Outcome` and one-shot `run` handler used
at test-registration boundaries. It handles the ordinary
`core.error.throwing<core.string.String>` effect from a unit-returning action;
normal return passes and every thrown owned message becomes `Failed(message)`.

Naming `Option` or `Result` requires an ordinary root alias such as
`let Option = core.Option` or `let Result = core.Result`.

`core.numeric` extends every primitive integer with `min`, `max`, `clamp`,
and `sign`. Signed integers return a same-width unsigned value from
`magnitude`, including at the signed minimum; unsigned magnitude is the
identity. `value.checked_into(output: target)()` returns
`core.Option<Target>` and accepts only another integer type. A value outside
the target range produces `None`; there is no implicit, wrapping, or
truncating fallback. Invalid `clamp` bounds trap.

These methods have one canonical owner and are not mirrored through `std`.
Their compiler intrinsics preserve the source contract in CTFE and native
code, including `isize`/`usize` at the compiler's explicit target width.

Their inherent helper surface is allocation-free:

| Type | Inspection/view | Transform | Fallback/conversion |
| --- | --- | --- | --- |
| `Option<T>` | `is_some`, `is_none`, `as_ref` | `map`, `and_then` | `unwrap_or`, `unwrap_or_else`, `ok_or`, `ok_or_else` |
| `Result<Error><T>` | `is_ok`, `is_err`, `as_ref` | `map`, `map_error`, `and_then` | `unwrap_or`, `unwrap_or_else`, `ok`, `err` |

`as_ref()` preserves the receiver region and defaults to shared access;
`as_ref<mut>()` requires an exclusive receiver and produces exclusive payload
borrows. Matching a borrowed enum inspects its discriminant and aliases its
payload storage instead of moving it. The returned view therefore cannot
outlive the source, and an exclusive view blocks overlapping access.
Transform callbacks and lazy fallbacks forward their declared effect row and
run only in the selected variant. `unwrap_or` and `ok_or` take eagerly
evaluated values; the `_else` forms evaluate their callback only on `None` or
`Err`. All consuming helpers evaluate and move each payload at most once.

`Movable` is an automatically satisfied structural marker for relocatable values. `Copyable` has the
supertrait constraint `trait(requires: self is Movable)`, while `Droppable` remains independent: an owning resource may
be movable without being copyable. Source code does not need handwritten `Movable` implementations
for ordinary aggregates.
Operators and syntax that lower through these identities use the validated standard-library
declarations directly; aliasing is only required when source code writes the short names.

`core.ops` is a compatibility facade over smaller protocol modules. `core.ops.arith` defines
`Add`, `Sub`, `Mul`, `Div`, `Rem`, and `Neg`; `core.ops.bit` defines `Not`, `BitAnd`, `BitOr`,
`BitXor`, `Shl`, and `Shr`; `core.ops.assign` defines the compound-assignment protocols; and
`core.cmp` defines `Eq`, `PartialOrdering`, and `PartialOrd`. The `core.ops` facade re-exports the
operator-facing names for ordinary aliases. They are not in the prelude.
Arithmetic and bitwise protocols accept their operands with automatic passing and use an associated
`Output` type. `Copyable` operands remain usable; resource operands move:

```sc fragment
let Add = core.ops.Add

extend(Number, Add<Number>) {
  let Output = Number
  let add(self)
    (rhs: Number): Number = { ... }
}
```

`eq(rhs)` borrows both operands and returns `bool`; `!=` invokes the same method exactly once and
negates its result:

```sc fragment
let Eq = core.ops.Eq

extend(Number, Eq<Number>) {
  let eq(self: Borrow<self>)
    (rhs: Borrow<Number>): bool = { self.value == rhs.value }
}
```

`partial_ord(rhs)` also borrows both operands. Its `partial_cmp` method returns `PartialOrdering`,
whose variants are `Less`, `Equal`, `Greater`, and `Unordered`. All four ordering operators invoke
the method once; an `Unordered` result makes each operator false:

```sc fragment
let PartialOrd = core.ops.PartialOrd
let PartialOrdering = core.ops.PartialOrdering

extend(Number, PartialOrd<Number>) {
  let partial_cmp(self: Borrow<self>)
    (rhs: Borrow<Number>): PartialOrdering = { ... }
}
```

`Neg` and `Not` use automatic passing for their operand and define an associated `Output` type. Consequently an
overloaded `!` may return a non-boolean result; only the built-in boolean operation is fixed to
`bool`. The boolean implementation is ordinary source control flow, and signed
integer negation is defined as subtraction from zero. Generic code can state
the same output relationship in a normal where predicate.

`bit_and(rhs)`, `bit_or(rhs)`, `bit_xor(rhs)`, `shl(rhs)`, and `shr(rhs)` have the same two automatic
parameter groups and associated `Output` shape as arithmetic protocols. Built-in integer shifts use
arithmetic right shift for signed integers and logical right shift for unsigned integers. Negative
or out-of-width shift counts trap instead of exposing backend undefined behavior.

`add_assign(rhs)`, `sub_assign(rhs)`, `mul_assign(rhs)`, `div_assign(rhs)`, `rem_assign(rhs)`,
`bit_and_assign(rhs)`, `bit_or_assign(rhs)`, `bit_xor_assign(rhs)`, `shl_assign(rhs)`, and
`shr_assign(rhs)` are separate mutation protocols. Each mutably borrows `self`, accepts `rhs` with
automatic passing, and
returns `()`:

```sc fragment
pub let AddAssign<Rhs: type> = trait {
  let add_assign(self: Borrow<mut><self>)
    (rhs: Rhs): ()
}
```

The corresponding `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, and `>>=` syntax binds to
these validated identities for nominal values. Built-in integers use the same fixed operator
semantics, including division, remainder, and shift traps. The left place is resolved once; an
inherent or unrelated trait method with the same member spelling cannot intercept compound
assignment. Their standard implementations are source definitions of the form
`self = self + rhs`; only the underlying scalar operation is intrinsic.

Writing `left + right`, `left & right`, `left == right`, or `left < right` does not itself require an
alias. An alias is required when source names the protocol in an implementation, bound, type, or
direct member access.

`core.flow` contains the standard protocols for `?.` and `??`. They are not in the prelude:

```sc fragment
pub let Chain = trait {
  let Item: type
  let Rebind<Value: type>: type

  let chain<e: effects, U: type>: with<e>
    (self)
    (transform: with<e>((Item): U)): Rebind<U>
}

pub let Coalesce = trait {
  let Item: type

  let coalesce<e: effects>: with<e>
    (self)
    (fallback: with<e>((): Item)): Item
}
```

The protocols use the same trait and generic-associated-constructor syntax as user declarations.
The compiler lowers GAT references in trait method signatures and supports direct constructor
implementations such as `let Rebind = Maybe` plus partially applied type aliases. GAT parameters
may carry `type`, `access`, `region`, `usize`, and closed-value sorts; implementation constructors
must match those sorts and parameter-group boundaries, not merely their arity. `??` dispatches non-`Option`/`Result` nominal
values through `Coalesce` when the fallback can be represented as a no-capture lifted function. `?.`
dispatches non-`Option`/`Result` nominal values through `Chain` when the synthesized transform
closure can be represented in the same way; simple field access is supported, while transforms that
capture outer method-call arguments still require the general callable-to-function bridge. The
facade `core.Option`/`core.Result` paths remain available as standard-library specializations.

`core.effect` owns standard effect identities. It is not part of the prelude; ordinary source
should alias these identities through `core.effect`:

```sc fragment
pub let unsafety = effect {}

pub let throwing<Error: type> = effect {
  let raise(move error: Error): never
}

pub let suspension = effect {
  let suspend(): ()
}
```

`unsafety`, `throwing<Error>`, and `suspension` are validated lang-item identities, but their declarations use
the same source-level effect forms as user code. `failure.raise` is an ordinary `never`-returning
effect operation and can be handled with a normal abort clause such as `raise: { (error) -> ... }`.
Standard and user effect identities use `snake_case`, including the final
segment of a `with<...>` effect path. Effect
row parameters such as `e: effects` are resolved as parameters rather than nominal effects.
Source `throw(error)` targets this ordinary operation when the current effect row has exactly one
active `throwing<Error>`. Contextual `try { ... }` with an expected `Result<Error><T>` handles
ordinary `throwing<Error>` through the same algebraic handler path, using `done -> Ok` and
`raise -> Err`. Without an explicit `Result` context, direct calls and local function-value calls
to ordinary `throwing<Error>` functions infer the same handler result when the success type is
probeable and the escaping error type is unique. `suspension` currently exposes only a minimal
`suspend(): ()` operation; executable
async/future lowering will add its handler contracts in the same implementation slice rather than
pretending `await` already works.

`core.sorts` owns standard compile-time sorts, also outside the prelude:

```sc fragment
pub let type: sort<2>
pub let region: sort<2>
pub let effect: sort<2>
pub let effects: sort<2>
pub let parameters: sort<2>
pub let constraint: sort<2>
pub let abi = sort<1> {
  c
}
```

Inside a compiler-owned `requires(...)` guard, `left is right` selects the `is`
relation between the classifiers of its operands. `type` implements
`Is<constraint>`, allowing function guards such as
`requires(T is Copyable)` and extension requirement groups such as
`(requires: T is Copyable)`.

`effect` classifies one nominal effect identity; `effects` classifies a normalized zero-or-more
effect row. Runtime `String` values are accepted by CTFE for compiler-consumed
UTF-8 metadata, and `abi` is a finite calling-convention sort whose first
supported value is `c`.

`core.borrow` owns the finite access sort and its unqualified aliases:

```sc fragment
pub let access = sort<1> {
  shared
  mut
}
pub let mut = access.mut
pub let shared = access.shared
```

`core.passing` owns the runtime parameter modifier functions:

```sc fragment
pub let copy<p: parameters>: parameters
pub let move<p: parameters>: parameters
```

Borrow types and values are written with the declared `Borrow` form: `Borrow<T>`,
`Borrow<mut><T>`, and `Borrow<a><r><T>`. `Borrow<a>` refers to the finite access sort; generic
passing modifiers use the `<p: parameters>: parameters` function sort. There
is no compile-time passing modifier: angle groups are compile-time by syntax.

`core.memory` declares the fixed-size `Array<T><l>`, unsized `Slice<T>`, and
`Ptr<a: access = shared><T>` raw-pointer family. `Slice<T>` is never a first-class stored value:
programs use `Borrow<a><r><Slice<T>>`, represented as a pointer and length while retaining the
source loan and region. Array borrows unsize contextually, and `Vec<T>.as_slice<a>()` borrows its
initialized prefix without transferring ownership.

The source-backed slice extension provides `len()` and bounds-checked `at(index)`. Shared access is
the default; `at<mut>(index)` returns a mutable element borrow when the slice borrow is mutable.
Out-of-bounds access traps. The pointer extension provides `offset(index)` for either access and
`init(value)` / `take()` only for `Ptr<mut><T>`. Pointer methods retain the `unsafety` requirement of
their underlying raw intrinsics; `init` expects uninitialized storage and `take` leaves storage
uninitialized.

`core.ops.index.Index<Key>` is the single bracket protocol. Its `index<a: access>` method returns
`Borrow<a><Output>`, so shared reads, explicit element borrows, and mutable assignment use one
implementation without a separate `index_mut`. Arrays implement `index(usize)` through a validated
core intrinsic; slice implements `index(u64)` in source by forwarding to `at`.

Capability modules are separated by semantics:

- `core.effect` owns generic handler infrastructure.
- `core.error` owns `throwing`, `throw`, and the `try` interpreter into `Result`.
- `core.async` owns `suspension`, `Poll`, `Future`, `Executor`, `async`, and `await`.
- `core.unsafe` owns the `unsafety` authority effect and its lexical interpreter.
- `core.result` owns only the `Result` data type and its ordinary protocols.
- `core.control` owns structural control flow: `break`, `continue`, `return`, `do`, `loop`,
  `while`, `if`, `match`, `for`, and lexical `defer`.

`throw` and `throwing` are not result-specific. `throwing<Error>` is an independent effect, while
`try` is one interpreter that chooses `Result<Error><T>` as its output. Other handlers may
interpret the same effect differently.

`core.effect` declares the protocol and erased runtime contracts used by algebraic handler lowering:

```sc fragment
pub let Continuation<Input: type, Output: type>: type
pub let EffectCallable<Input: type, Output: type, Answer: type>: type
pub let Handle = trait<self: effect> {
  let Clauses<Value: type, Answer: type>: parameters
  let handle<Value: type, Answer: type, rest: effects>: with<rest>
    ...Clauses<Value, Answer>
    (move action: with<self, rest>((): Value)): Answer
}
```

`Continuation` is a one-shot suspended computation. `EffectCallable` is an owned action awaiting a
handler-supplied continuation from `Output` to `Answer`; `Input` is the action's packed runtime input.
Both native values carry call and drop entries, an environment pointer, and an ownership flag. They
are `core.effect` exports rather than prelude names and cannot be replaced by same-named user
declarations.
The compiler-internal action entry has the logical signature
`(environment, input, Continuation<Output, Answer>): Answer`. Erasing or invoking an action consumes
its owner; a dropped, uninvoked action releases its captured environment through the stored drop
entry. Within an active handler, compatible open runtime action parameters use this representation
when crossing named effectful frames or another reusable handler. The source closure may have
shared, mutable, or moved captures, but the erased owner itself is always one-shot and cannot escape
with a borrow-capturing environment. `handle` is an effect-kinded lang trait automatically satisfied by every source
`effect` declaration. Its `Clauses` associated parameter schema names the compiler-derived labeled
clause groups used by `.handle`; `...` expands that schema into an ordered sequence of runtime
parameter groups. Consequently source calls use named trailing closures directly, for example
`state<i32>.handle get { ... } put { ... } action { ... }`, while the generated implementation has exactly the
shape declared by the trait. These low-level operations and generated handler implementations are
not ordinary source-level standard-library functions.

`core.async` makes the asynchronous model explicit in source. `Future<e>` is a `Movable` trait with an
associated `Output` and a mutable-borrowing `poll` method returning `Poll`. `Executor.run` is
an allocation-free protocol. The ordinary zero-field `std.async.spin`
implementation repeatedly polls one owned future until `Ready`; the concrete
polling policy is intentionally above the freestanding protocol.
Constructing a cold future does not select or run an executor.
`async` remains the direct intrinsic that materializes the anonymous future
state selected for its action, while `await` is source-defined. Their
signatures expose their effect rows and `Future<e>` plus `Output == T` relationship.
`await` repeatedly calls `poll`; `Pending` invokes
`suspension.suspend()`, and `Ready(value)` exits the source loop. The compiler may
take an equivalent syntax-directed state-machine path for `await`.
Compiler-generated futures without suspension already
implement the inferred `Future<e>` instance and transition from cold state to `Poll.Ready` exactly
once. `e` may be empty, `unsafety`, or a custom residual effect. A body without suspension can poll
under the corresponding algebraic handler through generated poll/resume source specialization
when its captures are by-value `Copyable` or move-only values. Move-only fields transfer exactly once
and are not dropped again with completed future state. Borrowed, suspended, and `throwing`-residual
bodies remain compiler work. Polling
enforces `e` while construction remains pure. A single tail-position `await` creates its child on the first parent poll,
stores it across `Pending`, and completes the parent from `Ready`; cancellation drops a stored child
exactly once. One non-tail `let value = await child` may continue with a linear suffix whose captures
are retained in parent state. Sequential awaits compose through nested continuation futures and
preserve earlier results across later `Pending` states. Suspension nested in control flow remains
compiler work. Locals live across a sequential suspension are state fields with ordinary ownership
and cleanup. A borrow cannot cross suspension together with a local referent stored in that same
Future state cannot retain such a borrow because `Future` requires `Movable`;
external region-checked borrows remain permitted.
`if` and `match` branches consisting of one tail await select their child before using this same
polling contract. Different concrete child types use a private active-variant future when their
output agrees. Each branch retains its own linear locals across suspension; a branch without await
is an immediate `Ready` future. Loop suspension remains compiler work.

```sc fragment
pub let do<e: effects, T: type>: with<e>
  (move action: with<e>((): T)): T
pub let do<e: effects>: with<e>
  (move action: with<core.control.loop_exit<()>, core.control.iteration_skip, e>((): ()))
  (move while: with<core.control.loop_exit<()>, core.control.iteration_skip, e>((): bool)): () = {
  loop {
    core.control.iteration_skip.handle
      next { () }
      action { action() }
    if while() { continue() } else { break() }
  }
}
pub let try<f: effects, T: type, E: type>: with<f>
  (move action: with<core.error.throwing<E>, f>((): T)): core.Result<E><T>
pub let throw<Error: type>: with<core.error.throwing<Error>>
  (move error: Error): never
pub let unsafe<e: effects, T: type>: with<e>
  (move action: with<core.unsafe.unsafety, e>((): T)): T
pub let loop<e: effects, T: type>: with<e>
  (move body: with<core.control.loop_exit<T>, core.control.iteration_skip, e>((): ())): T
pub let while<e: effects>: with<e>
  (move condition: with<e>((): bool))
  (move do: with<e>((): ())): ()
pub let if<e: effects, T: type>: with<e>
  (condition: bool)
  (move then: with<e>((): T))
  (move else: with<e>((): T)): T = {
  match condition
    { true -> then() }
    { false -> else() }
}
pub let match<Input: type, Output: type, e: effects, ...cases: parameters>: with<e>
  (move input: Input)
  ...cases: Output
pub let for<e: effects, Iterable: type, Iter: type, Item: type>: with<e>
  (move iterable: Iterable)
  (move body: with<core.control.loop_exit<()>, core.control.iteration_skip, e>((Item): ())): () =
requires(
  Iterable is core.iter.IntoIterator &&
  Iterable.IntoIter == Iter &&
  Iter is core.iter.Iterator &&
  Iter.Item == Item
)
```

Here `try` removes only `throwing<E>`, `unsafe` removes only the `unsafety` requirement, and both forward
the remainder row. `throw` introduces the standard `throwing<Error>` requirement. `loop` and `for`
handle their declared `loop_exit`/`iteration_skip` effects while forwarding `e`; `if` and `match` evaluate
only the selected lazy branch or case. The source definitions that do not require intrinsic
lowering remain intentionally simple:

```sc fragment
pub let do<e: effects, T: type>: with<e>
  (move action: with<e>((): T)): T = {
  action()
}

pub let try<f: effects, T: type, E: type>: with<f>
  (move action: with<core.error.throwing<E>, f>((): T)): core.Result<E><T> = {
  core.error.throwing<E>.handle raise { (error) -> core.Result.Err(error) } done { (value) -> core.Result.Ok(value) } action {
    action()
  }
}

pub let throw<Error: type>: with<core.error.throwing<Error>>
  (move error: Error): never = {
  core.error.throwing<Error>.raise(error)
}
```

`core.iter` owns iteration rather than the prelude:

```sc fragment
pub let Iterator = trait {
  let Item<r: region>: type
  let next<r: region>(self: Borrow<mut><r><self>)
    (): core.Option<Item<r>>
}

pub let IntoIterator = trait {
  let IntoIter: type
  let into_iter(move self)
    (): IntoIter
}

pub let ArrayIntoIter<T: type>
  <l: usize> = struct { ... }

pub let OwnedItem<T: type><r: region>: type = T
pub let BorrowedItem<a: access, T: type><r: region>: type =
  Borrow<a><r><T>

pub let SliceIter<a: access><T: type> = struct { ... }
```

Implementing or naming either trait requires aliases such as
`let Iterator = core.iter.Iterator` and `let IntoIterator = core.iter.IntoIterator`. The `for`
syntax itself needs no alias and dispatches only through these validated identities. It evaluates
the iterable once, moves it into `IntoIterator.into_iter`, repeatedly mutably borrows the resulting
iterator for `Iterator.next`, and stops on `None`. An inherent or unrelated trait method named
`into_iter` or `next` cannot intercept this lowering.

`Array<T><l>` implements consuming value iteration when `T: Copyable`. A borrowed `Slice<T>` exposes
access-polymorphic `.iter<a>`: `SliceIter<a><T>` stores the source loan and yields
`Borrow<a><r><T>` for the region of each `next<r>` receiver borrow. Shared iteration therefore
works for non-`Copyable` elements without moving them, while mutable iteration yields exclusive
element borrows. A yielded mutable borrow must end before the next call to `next`; the source
remains borrowed until the iterator is consumed or leaves scope. `Vec<T>` implements consuming
iteration for all element types. Its iterator transfers the allocation, moves values in source
order, and on early exit drops exactly the unyielded suffix before releasing storage.

The control spellings bind to these validated identities without aliasing ordinary names. Standard
effect identities such as `throwing` remain normal `core.effect` exports when named in source, backed
by `core.effect` identities. An ordinary same-named declaration cannot acquire lang-item lowering
behavior. future control features
follow the same rule: for example, async lowering must add `Future`, `async`, and handler contracts
to the matching core release when it becomes executable, rather than reserving undocumented compiler
magic in advance.

`std.algebra` contains opt-in first-order algebra protocols rather than putting them in `core` or the prelude:

```sc fragment
pub let Semigroup = trait {
  let combine(left: self, right: self): self
}

pub let Monoid = trait(requires: self is Semigroup) {
  let empty(): self
}
```

The compiler does not prove algebraic laws.

`std.functional` contains higher-kinded protocols over compile-time type constructors. It is not
part of the prelude:

```sc fragment
pub let Functor = trait<self: <Value: type>: type> {
  let map<e: effects, A: type, B: type>: with<e>
    (self: self<A>)
    (transform: with<e>((A): B)): self<B>
}

pub let Applicative = trait<self: <Value: type>: type>(requires: self is Functor) {
  let pure<A: type>
    (value: A): self<A>

  let apply<e: effects, A: type, B: type>: with<e>
    (self: self<with<e>((A): B)>)
    (value: self<A>): self<B>
}

pub let Monad = trait<self: <Value: type>: type>(requires: self is Applicative) {
  let flat_map<e: effects, A: type, B: type>: with<e>
    (self: self<A>)
    (next: with<e>((A): self<B>)): self<B>
}
```

These declarations use constructor sorts such as `<Value: type>: type` on the trait `self` subject,
not as ordinary trait parameters. Traits with a matching constructor subject can be implemented for
generic nominal constructors. Method implementations are registered as generic function templates
and validated, for example
`extend(Carrier, Functor) { let map<e: effects, A: type, B: type> ... }`.
Receiver methods
dispatch from concrete nominal instances, so `Carrier<i32> { value: 41 }.map(add_one)` selects the
`Carrier: Functor` implementation and instantiates the generic method template. Constructor
associated functions without a receiver can still be called from the bare constructor; for example,
`Carrier.pure(...)` is available once `Carrier` implements `Applicative`. Trait-level `where`
constraints express protocol inheritance, so a
`Carrier: Applicative` implementation also requires `Carrier: Functor`, and `Carrier: Monad`
requires `Carrier: Applicative`.

The standard library implements `Functor`, `Applicative`, and `Monad` for `core.Option` and for
each partially applied `core.Result<Error>` constructor:

```sc fragment
let Result = core.Result
let Monad = std.functional.Monad

let next(value: i32): Result<bool><i32> = {
  Result<bool><i32>.Ok(value + 1)
}

let value = Result<bool><i32>.Ok(41).flat_map(next)
```

Curried constructors may be used as constructor trait implementation targets, which is how
`Result<Error>: Monad` is expressed without making `Result` special. `Option` and `Result` are
ordinary enum values and require explicit constructors. Language error propagation is defined by
the standard `throwing<E>` effect, `throw`, and `try { ... }`; `do` has no error-specific semantics.

Primitive implementations remain compiler-defined. The unit type has the single spelling `()`. A declaration only
receives language-item behavior when its validated identity comes from this edition's embedded core;
same-named user declarations do not gain special semantics.

See [standard-library organization](README.md) for the prelude/alias policy and
[the language specification](../language/specification.md) for semantic rules.

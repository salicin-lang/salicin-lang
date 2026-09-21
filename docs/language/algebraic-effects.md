# Algebraic-Effect Contracts

This document defines the implementation contract for source-declared algebraic effects. The
[language specification](specification.md) defines their observable semantics.

## Effect Declarations

An effect is a nominal compile-time identity with zero or more operations:

```sc fragment
let state<S: type> = effect {
  get(): S
  put(move value: S): ()
}
```

An operation has explicit runtime parameter groups, passing modes, result type, and optional
forwarded effects. A complete operation call performs the effect. Partial application is pure until
the final group is supplied.

Effect declaration parameters and effect-identity arguments are compile-time
groups and therefore use angle brackets exclusively, as in `state<S>`.

Operations use callable declarations of the form `name(parameters): Result`: they omit
`let` and `=`, use `snake_case` names, and cannot have bodies. They are selected through
their effect identity and obey ordinary visibility and overload rules. A
declaration with the same operation name in another effect is unrelated.

## Effect Rows

`with<E>` adds the normalized effect row `E` to a callable signature or type:

```sc fragment
let increment with<state<i32>>(): i32 = {
  let value = state<i32>.get()
  state<i32>.put(value + 1)
  value
}

let apply<e: effects> with<e>
  (action: with<e>(i32): i32)
  (value: i32): i32 = {
  action(value)
}
```

An ordinary named callable attaches its signature directly to the declaration name
and places it before `=`; trait and effect members use the same shape. A
function value uses the callable type `with<state<i32>>(): i32`. The row belongs to
the complete multi-group call, not to a parameter group or result value.
`with<>(a): b` is the pure callable `(a): b`; a non-callable operand is
rejected.

Rows are unordered sets of nominal effect identities. Handling one identity removes exactly that
identity and forwards every other requirement. A `e: effects` parameter may represent
an abstract residual row and is instantiated before runtime lowering.
The singular `effect` sort classifies one identity; the plural `effects` sort classifies the empty
row (`pure`) or any normalized combination of identities and row variables.

Effect rows do not encode ordinary allocation, I/O, or mutation. Those capabilities are represented
by library types and APIs unless they explicitly declare an effect.

## Handler Shape

Every source effect is validated against `core.effect.Handle`. Its derived
`handle` function has one ordinary labeled Brace argument group containing:

- one closure for each operation;
- an optional `done` closure for normal completion and answer-type conversion;
- one final `action` closure evaluated under the handler.

Conceptually:

```sc fragment
let answer = state<i32>.handle {
  get: { (resume) => resume(41) },
  put: { (value, resume) => resume(()) },
  done: { (value) => value },
  action: { increment() + 1 },
}
```

There is no dedicated handler grammar or parser rewrite. This is an ordinary
declaration-directed call to the compiler-generated function: labels select its
derived parameters and every value is an explicit closure. Omitting `done`
preserves the action result unchanged.

Operation-closure parameter and result types come from the effect declaration.
Overloaded operations retain their declared parameter labels so each closure
remains unambiguous.

An operation returning `never` is abortive. Its closure has no continuation and directly produces
the handler answer.

## Continuations

A resumable operation closure receives a delimited, single-use continuation. Calling it:

1. supplies the operation result;
2. resumes the suspended action;
3. eventually produces the complete handler answer.

The continuation owns its suspended frames and cleanup state. It cannot be copied, invoked twice,
or outlive captured borrows. Dropping it abandons the suspended computation and cleans every
initialized captured value exactly once.

Resumption and abandonment are both ordinary ownership paths. Neither path may leak captures,
duplicate drops, or silently skip destructors.

## Standard Effects

`core.error.throwing<Error>` is the standard abortive error effect. Its `Raise` operation returns
`never`. `throw(error)` invokes that operation. `try { action }` is one standard interpreter that
handles it into `core.Result<Error><Value>`; the effect itself is independent of `Result`.

`core.unsafe.unsafety` is an authority effect. Its handler is the lexical `unsafe { ... }` boundary.
Authorization does not weaken type checking, ownership, region checking, or cleanup.

Standard effects use the same nominal row and handler machinery as user effects. Their source
declarations are validated lang items, not name-based exceptions.

Their names describe the behavior or capability rather than repeating the
declaration kind: `throwing`, `suspension`, `unsafety`, `loop_exit`,
`iteration_skip`, and `function_exit`. The `with<...>` position and nominal
identity distinguish them from types and traits without an `_effect` suffix.
This naming rule is enforced for the embedded standard library only.

## Selective CPS Lowering

The compiler may lower only effectful call paths into continuation-passing form. Pure functions and
unaffected paths retain their ordinary ABI.

For a handled path, lowering must preserve:

- source-order argument evaluation;
- parameter passing modes;
- the exact residual effect row;
- operation identity and overload labels;
- one-shot continuation ownership;
- lexical borrow bounds;
- cleanup flags for initialized captures;
- the handler answer type;
- source locations in diagnostics.

Known named effectful functions, their aliases, and statically selected callable alternatives may
specialize into CPS frames. An unknown callable must not be silently treated as pure.

## Runtime Contracts

`Continuation<Input, Output>` and `EffectCallable<Input, Output, Answer>` are
source-declared type forms with complete core-private `builtin()` initializers
and compiler-owned representations. They are not empty
structures, and their values are linear resources.

The runtime representation may use generated frames and adapters, but those details are not
observable language entities. Generated names must not appear in user diagnostics or participate in
source lookup. A continuation currently cannot escape its operation closure.
Consequently `suspension.handle` can interpret `suspension.suspend()` directly, but a
source handler cannot yet store the suspended continuation as future state;
`core.async.async` remains the compiler boundary that materializes that state.

## Rejection Boundaries

The compiler rejects a handler when it cannot prove:

- a unique effect and operation identity;
- a complete, nonduplicated arm set;
- compatible arm and answer types;
- safe continuation ownership;
- valid capture lifetimes;
- preservation of residual effects;
- deterministic cleanup.

Unsupported dynamic effect dispatch is rejected rather than approximated with an incorrect pure
call or an erased effect row.

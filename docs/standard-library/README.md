# Standard library

Salicin reserves three canonical library layers:

- `core` contains allocation-free language protocols and fundamental types.
- `alloc` contains owning heap types and depends on the allocator ABI.
- `std` owns policy-bearing or host-facing abstractions and does not mirror
  lower-layer modules.

The dependency order is `core ← alloc ← std`: `alloc` correctly depends on
`core`, while `core` never depends on allocation or host services.

Source is organized around canonical definition modules:

```text
library/
  core/src/
    lib.sc
    prelude.sc
    never.sc
    marker.sc
    option.sc
    result.sc
    error.sc
    cmp.sc
    flow.sc
    ops.sc
    ops/arith.sc
    ops/bit.sc
    ops/assign.sc
    effect.sc
    async.sc
    unsafe.sc
    sorts.sc
    string.sc
    borrow.sc
    memory.sc
    numeric.sc
    control.sc
    iter.sc
  alloc/src/
    lib.sc
    boxed.sc
    vec.sc
    raw.sc
  std/src/
    lib.sc
    async.sc
    algebra.sc
    functional.sc
```

## Prelude policy

The edition prelude must stay small. It contains the universal `never`, `Copyable`, and `Droppable`
contracts, primitive type names, and the `Array`, `Ptr`, `size_of`, and `align_of`
memory contracts that compiler-generated types and low-level library code routinely need.
`Option` and `Result` are fundamental `core` declarations:

```sc fragment
let Option = core.Option
let Result = core.Result
```

Operator traits are aliased from the `core.ops` facade, `?.`/`??` protocols from `core.flow`, generic
handler contracts from `core.effect`, typed failure from `core.error`, asynchronous computation from
`core.async`, unsafe authority from `core.unsafe`, compile-time sorts from `core.sorts`,
compiler-lowered control contracts from `core.control`, algebra protocols from
`std.algebra`, higher-kinded functional protocols from `std.functional`, iteration protocols from
`core.iter`, and owning containers from `alloc.boxed` and `alloc.vec`.
Declarations should be named through their canonical layer or given
transparent aliases with ordinary `let`; for example:

```sc fragment
let Box = alloc.boxed.Box
let Vec = alloc.vec.Vec
let String = core.string.String
```

The compiler validates and embeds the matching `library/std` source bundle
alongside the lower-level `core` and `alloc` namespaces. `std` contains only
its own declarations; it does not manufacture duplicate lower-layer paths.
Its source uses canonical qualified paths internally: private shortcuts and
public re-exports are both rejected as mirrors. Re-export facades are reserved
for the explicitly documented `core.lib`, `core.prelude`, and `core.ops`
surfaces.
No declaration can obtain compiler authority by copying a privileged name or
shape.
Unsupported hosts are rejected before semantic analysis. The initial
supported pairs are Linux/x86-64 and macOS/arm64.
Non-prelude declarations have qualified internal identities, so a user declaration without such an alias may
still be named `add`, `Box`, or `Vec`. A project dependency or top-level file module cannot claim
any of these standard namespaces.
`core.ops` uses the same rule: `Add`, `Sub`, `Mul`, `Div`, `Rem`, `Eq`, `PartialOrdering`,
`PartialOrd`, `Neg`, `Not`, `BitAnd`, `BitOr`, `BitXor`, `Shl`, `Shr`, and their `*Assign` mutation
traits require ordinary aliases when
named. Merely writing the corresponding operator token does not require importing its protocol.
`core.flow.Chain` and `core.flow.Coalesce` require ordinary aliases when named directly.
`throwing<E>`, `unsafety`, and `suspension` are ordinary standard effect declarations in `core.error`,
`core.unsafe`, and `core.async`. Source that names them binds them normally. `try` and `throw` target
`core.error`; `unsafe` targets `core.unsafe`; structural control spellings such as `do` and `loop`
target `core.control`. These contextual spellings do not inject module exports as ordinary
unqualified names.
Effect identities and row parameters use `snake_case`; for example,
`<e: effects>`.
Standard declaration names describe semantics rather than encoding their
kind: types use entity/state nouns, traits use capability/role/operation
names, and effects use abstract behavior or capability nouns such as
`throwing`, `suspension`, and `unsafety`. Embedded public types, type parameters, type forms,
traits, variants, and associated types use ASCII `PascalCase`; functions,
methods, values, fields, modules, effects, and sorts use ASCII `snake_case`.
Names may not use category suffixes such as `_type`, `_trait`, or `_effect`;
ordinary user declarations are not subject to this library gate.
The `effect` identity sort, `effects` row sort, finite `access` sort, and parameter modifier functions use
contextual names such as `pure`, `shared`, `mut`, `copy`, and `move` in parameter positions.
`Semigroup` and `Monoid` require aliases from `std.algebra` when named.
`Functor`, `Applicative`, and `Monad` require aliases from `std.functional` when named.
`Iterator` and `IntoIterator` require ordinary aliases from `core.iter` when named in an implementation
or bound. Writing `for pattern in value { ... }` binds to their validated lang-item identities
without aliasing them and cannot be redirected by same-named inherent methods or traits.

The compiler, library sources, and edition form one toolchain unit. Compiler-matched language items
must come from the matching `core`, while user declarations with the same spelling remain ordinary
declarations. `std`, `core`, and `alloc` are reserved top-level namespaces, not manifest
dependencies.

The complete [`examples/inventory`](../../examples/inventory) package exercises
file modules, canonical runtime string literals across module boundaries, a
vector of non-`Copyable` values, consuming iteration, and user trait dispatch.

The accepted [initial surface contract](../project/standard-library-surface.md) defines the next
modules, host boundary, failure policy, and minimum API matrix.

# Effect Callable Syntax

Status: accepted and implemented for Edition 2026

## Contract

`with<E>` prefixes a callable type and adds the normalized effect row `E`:

```salicin
with<io>(str): String
with<e>(i32): i32
```

The row belongs to the complete callable, including every runtime parameter
group. `with<>(A): B` is equivalent to the pure callable `(A): B`.
A callable type always ends in a colon followed by its result type.

An effectful declaration places all signature groups after `=`:

```salicin
let read = with<io>(path: str): String => ...

let apply = <e: effects> with<e>
  (action: with<e>(i32): i32)
  (value: i32): i32 => {
  action(value)
}
```

The final colon introduces the declaration result. A pure declaration uses the
same RHS signature structure:

```salicin
let identity = (value: i32): i32 => value
```

`let f = (...): with<e>(R)` is not an effect annotation: it attempts to use
`with` as a non-callable result and is rejected. This keeps the result position
available for future task or computation types.

## Migration

The Edition 2026 grammar, library sources, documentation, and fixtures use only
the prefix effect form and colon-delimited callable results.

The surface rewrite does not change the semantic representation:
`Type::Function` continues to carry one normalized row. It therefore does not
change handler lowering, cleanup, ownership, or calling convention.

## Research Basis

Recent modal-effect work shows that effect tracking can be separated cleanly
from the underlying function type, which motivates making `with<E>` an
explicit callable constructor rather than decorating a result. Recent work on
linear effects and automatic resource analysis also reinforces that exception
and handler syntax must preserve cleanup and resource-safety semantics; this
migration deliberately changes no such semantics.

- [Rows and Capabilities as Modal Effects (POPL 2026)](https://doi.org/10.1145/3776674)
- [Linear Effects, Exceptions, and Resource Safety (ESOP 2026)](https://link.springer.com/chapter/10.1007/978-3-032-22720-1_8)
- [Handling Exceptions and Effects with Automatic Resource Analysis (OOPSLA 2026)](https://arxiv.org/abs/2603.02260)

# Initial Standard-Library Surface

Status: accepted for the 2026 edition  
Accepted: 2026-07-27

This contract fixes the first usable `core`, `alloc`, and host `std` surface.
It is an implementation target, not a claim that every declaration below
already exists. The [TODO](todo.md) owns the remaining implementation order.

## Design rules

The surface follows six rules.

1. Public embedded-library types and type parameters, type forms, traits, enum
   variants, and associated types use ASCII `PascalCase`; functions, methods,
   values, fields, modules, effects, and sorts use ASCII `snake_case`. This is
   an official standard-library public API style and validation rule, not a
   language-wide casing restriction. The primitive types `bool`, integers,
   `str`, and `never`, and the primitive values `true` and `false`, remain
   lowercase.
2. The prelude contains only names needed pervasively by ordinary syntax.
   Allocation, failure, formatting, collections, and host access stay
   qualified or use explicit local aliases.
3. `core` needs neither allocation nor a host. `alloc` may allocate but may
   not access a host. `std` is the only layer that may expose host services.
4. Safe APIs preserve ownership, initialization, UTF-8, and borrow
   invariants. An unchecked operation requires `unsafety`; it is not
   made safe merely by living in the standard library.
5. `io` is visible host authority, not an error-transport mechanism. Host
   failures are values returned in `Result<IoError><T>`.
6. Each declaration has one canonical definition module. Library source uses
   that path directly; mirror aliases and per-module re-export facades are
   forbidden in `std`. The deliberately small `core.lib`, `core.prelude`, and
   operator-oriented `core.ops` facades are explicit exceptions.
7. Each ordinary operation has one canonical name. Overloads may share a name
   only when they have the same semantics and are unambiguous from their
   inputs; return-type-only overloads are forbidden.

Names prioritize clarity at the call site. Boolean queries use `is_` or
`has_`; mutating operations use imperative verbs; consuming transformations
use `into_`; borrowed projections use `as_`; checked integer conversions use
`checked_into`; unchecked operations end in `_unchecked`.

### Naming by semantic category

The standard library does not encode declaration kinds in suffixes. Source
context already distinguishes a trait bound, a value type, an effect row, and
a callable. Public embedded-library names therefore follow this vocabulary:

| Declaration | Naming form | Examples |
| --- | --- | --- |
| struct, enum, or type form | `PascalCase` entity, value, or state noun | `String`, `Option`, `Poll` |
| trait | `PascalCase` capability adjective, role noun, or operation protocol | `Copyable`, `Iterator`, `Add` |
| effect | abstract behavior, event, or capability noun; a gerund when it is clearer | `throwing`, `suspension`, `unsafety`, `io` |
| function or method | action verb, with `is_`/`has_` for predicates | `write_all`, `is_empty` |
| value | `snake_case` state or value noun/adjective | `default_limit` |
| enum variant | `PascalCase` state or value noun/adjective | `Pending`, `Ready`, `None` |
| sort | the classified concept | `type`, `effect`, `effects`, `parameters` |

The standard effects are named `throwing<Error>`, `suspension`, `unsafety`,
`loop_exit<T>`, `iteration_skip`, and `function_exit<T>`. The enclosing module
and use position provide any further qualification, for example
`with<core.error.throwing<E>>`. Names such as `async_effect`,
`iterator_trait`, and `message_type` are rejected in embedded public library
source. This restriction is a standard-library quality gate, not a restriction
on ordinary user declarations.

This choice follows the practice of naming effect constants for the behavior
they document. The current Koka language guide uses semantic labels such as
`console`, `io`, and `ndet`; Flix describes effects as compiler-checked
documentation; recent higher-order-effect work uses domain labels such as
`Output` rather than category suffixes. Salicin uses category-aware
capitalization, while declaration context and semantic names avoid redundant
kind suffixes. [Koka language guide](https://koka-lang.github.io/koka/doc/book.html),
[Flix effect system](https://doc.flix.dev/effect-system.html),
[Hefty Algebras (JFP 2025)](https://doi.org/10.1017/S0956796825100142).

## Library layers and modules

### `core`

`core` is available to every target accepted by the compiler. It contains no
allocator or host symbol.

| Public module | Responsibility |
| --- | --- |
| `core.primitives` | `bool`, fixed-width integers, `isize`, and `usize` |
| `core.never` | `never` |
| `core.marker` | `Movable`, `Copyable`, and `Droppable` |
| `core.sorts` | compiler-owned static classifiers |
| `core.passing` | `copy` and `move` runtime parameter modifiers |
| `core.borrow` | `access`, `shared`, `mut`, and `Borrow` |
| `core.memory` | `Array`, `Slice`, `Ptr`, layout queries, and safe contiguous access |
| `core.option` | `Option` and its source-backed operations |
| `core.result` | `Result` and its source-backed operations |
| `core.cmp` | equality and partial-ordering protocols |
| `core.ops` | arithmetic, bit, assignment, and indexing protocols |
| `core.flow` | chaining, fallback, unwrap, and typed raising protocols |
| `core.iter` | iterator protocols and allocation-free algorithms |
| `core.numeric` | integer bounds, sign/magnitude helpers, and checked width conversion |
| `core.string` | canonical UTF-8 `String`, literals, construction/mutation, borrowed views, scalars, validation, and iteration |
| `core.fmt` | allocation-free parse, display, debug, and writer protocols |
| `core.effect` | effect-handler machinery |
| `core.error` | typed failure effect machinery |
| `core.control` | compiler-lowered structured control contracts |
| `core.unsafe` | `unsafety` and its handler boundary |
| `core.async` | the accepted cold-future surface |
| `core.foreign` | `abi` and the `foreign` initializer contract |

`core.lib` is a small root facade, not a second home for every declaration.
Definition modules own canonical identities. Compatibility aliases are not
added when a declaration moves.

### `alloc`

`alloc` depends only on `core` and the replaceable allocator ABI.

| Public module | Responsibility |
| --- | --- |
| `alloc.boxed` | the owning `Box<T>` allocation |
| `alloc.vec` | `Vec<T>` and consuming vector iteration |
| `alloc.string` | ownership-preserving `Vec<u8>`/`String` conversion and conversion errors |
| `alloc.fmt` | `StringWriter` and allocation-backed formatting helpers |

`alloc.raw` remains package-private. Safe source cannot call the allocator or
forge container metadata. Allocation failure and invalid allocation layout
remain process traps until the allocator ABI can represent recoverable
failure without weakening existing ownership guarantees.

### `std`

`std` is an edition-matched source bundle above `core` and `alloc`. It owns
policy-bearing and host-facing facilities without mirroring lower layers.

| Public module | Responsibility |
| --- | --- |
| `std.async` | concrete executor policies and future host runtimes |
| `std.algebra` | opt-in algebraic protocols |
| `std.functional` | opt-in higher-kinded functional protocols and standard implementations |
| `std.io` | byte readers/writers, standard streams, and `IoError` |
| `std.process` | process arguments and exit information |
| `std.fs` | paths, file options, owned files, and bounded convenience operations |
| `std.test` | failure values and assertion helpers |

The compiler embeds the edition-matched `library/std` sources. Public
definitions receive ordinary `std` identities and no compiler authority;
library implementations reference lower-layer declarations by their canonical
qualified paths. Private and public mirror aliases are forbidden. Duplicate
exports, dependencies outside the three standard layers, and unsupported
native targets are rejected. A user module or dependency cannot claim
`core`, `alloc`, or `std`.

The dependency order is `core ← alloc ← std`, where each arrow points to a
dependency. `core` is freestanding and dependency-free; `alloc` adds only
replaceable heap authority; `std` may use both and owns policy or host-facing
implementations. A declaration belongs in the lowest layer that can implement
it without importing a higher layer, but genericity alone does not make an
abstraction fundamental. Consequently `Option`, `Result`, iteration, operator
protocols, cold futures, and the executor protocol remain in `core`;
`Semigroup`, `Monoid`, `Functor`, `Applicative`, `Monad`, their standard
implementations, and the concrete `spin` executor belong to `std`.
Text, conversion, and formatting do not acquire parallel `std` modules:
the canonical string type and allocation-free operations use `core.string`,
numeric operations use `core.numeric`, and formatting protocols use
`core.fmt`; allocation-backed string helpers and format builders use
`alloc.string` and `alloc.fmt` without defining a second string type.

This boundary follows current freestanding-library practice rather than file
size or conceptual generality. Rust 1.97 describes `core` as dependency-free,
without heap allocation, concurrency, I/O, system libraries, or libc, while
its `alloc` layer owns heap-backed smart pointers and collections. Embedded
Swift likewise preserves full-language semantics while excluding library
facilities whose runtime dependencies are unavailable. Koka 3.2 keeps the
effect calculus small enough that async and other control abstractions can be
ordinary libraries. Salicin therefore classifies declarations by required
authority and semantic necessity, not by whether they happen to be generic.
[Rust `core`](https://doc.rust-lang.org/core/),
[Rust `alloc`](https://doc.rust-lang.org/stable/alloc/),
[Embedded Swift subset](https://docs.swift.org/embedded/documentation/embedded/languagesubset/),
[Koka 3.2](https://github.com/koka-lang/koka).

## Prelude

The 2026 prelude contains exactly:

- `never`, `Movable`, `Copyable`, and `Droppable`;
- `bool`, the fixed-width integers, `isize`, and `usize`;
- `Array`, `Ptr`, `size_of`, and `align_of`;
- `copy` and `move`;
- `shared` and `mut`.

`Borrow` remains contextual type syntax and its qualified declaration remains
available. `Slice`, `str`, `UnicodeScalar`, `Option`, `Result`, `Box`, `Vec`,
`String`, operator traits, iterator traits, formatting traits, error types,
effects, I/O, and assertions are excluded.

Compiler-recognized syntax may resolve a validated language item without
making its spelling an unqualified user name. In particular, operator tokens,
`?.`, `??`, structured control, `foreign<c>`, and `test<"name">` do not expand
the prelude.

## Ownership and borrowing

Public APIs use these modes consistently:

| Intent | Receiver or parameter | Result |
| --- | --- | --- |
| inspect a value | `Borrow<T>` | copied scalar or a borrow tied to the receiver |
| mutate in place | `Borrow<mut><T>` | `()` or a borrow tied to the exclusive receiver |
| transfer ownership | `move value: t` | a new owner or ownership-preserving error |
| accept cheap reusable input | automatic passing, with an explicit `Copyable` bound when required | never silently consumes a non-copy value |
| expose immutable contiguous data | `Slice<T>` or `str` | shared borrow only |
| expose mutable contiguous data | `Slice<mut><T>` | exclusive borrow; never for UTF-8 bytes |
| create a resource | host operation `with<io>` | `Result<IoError><Owner>` |
| operate on a resource | borrow the owner `with<io>` | result value; no hidden ownership transfer |
| close a resource | `move` the owner `with<io>` | `Result<IoError><()>` |

Borrowed views retain the source loan. An iterator yielding borrowed elements
cannot outlive that loan. Mutable iteration keeps one exclusive source loan
and cannot yield overlapping live element loans. Safe text APIs never expose
mutable UTF-8 bytes.

A consuming conversion that can fail returns the original owner in its error
when doing so is necessary to avoid data loss. `String.from_utf8(move bytes)`
therefore returns a `FromUtf8Error` that owns the rejected `Vec<u8>`.

Resource destruction is deterministic. `File.close(move self)` attempts one
close, consumes the logical handle even on error, and reports the error.
`Droppable.drop` also attempts close exactly once but cannot report failure;
programs that need the error must call `close` explicitly.

## Absence, failure, effects, and traps

The return form is selected by who can reasonably prevent or recover from the
condition.

| Form | Use |
| --- | --- |
| plain value | total operation for all valid inputs |
| `Option<T>` | ordinary absence with no useful error detail, such as `get`, `first`, `last`, `find`, or `pop` |
| `Result<E><T>` | malformed external data, checked conversion, allocation-independent parsing, or recoverable host failure |
| `with<effects>` | observable capability or control effect; never a substitute for a recoverable error value |
| trap | violated checked precondition, impossible safe invariant, fixed arithmetic trap, invalid allocation layout, or allocation failure |

Every trapping collection operation has a nearby checked alternative:
`get(index)` returns `Option<Borrow<T>>`, while `at(index)` and indexing trap
when out of bounds. A range operation validates the complete range before
forming a borrow or mutating storage.

UTF-8 validation, integer parsing, narrowing conversion, file open, read,
write, flush, seek, and explicit close do not trap for input or host errors.
`unwrap` and `at` deliberately trap and must document that fact. The standard
library does not add a general catchable panic mechanism in this milestone.

Callback-taking operations forward the callback's effect row exactly and
evaluate each input once. `io` and `unsafety` are distinct: safe host
operations require `io` but do not silently acquire `unsafety`; raw
pointer or unchecked representation operations require `unsafety`
whether or not they also perform I/O.

## Error families

Errors are small, inspectable values with no mandatory allocation.

| Error | Minimum information |
| --- | --- |
| `Utf8Error` | first invalid byte index and, when known, expected sequence length |
| `ParseIntError` | `Empty`, `InvalidDigit`, `InvalidSign`, or `Overflow`, plus the failing byte index when applicable |
| `IntConversionError` | source value was outside the destination range |
| `IoError` | portable `IoErrorKind` and optional signed raw host code |
| `TestFailure` | optional owned message plus source registration identity supplied by the runner |

`IoErrorKind` initially includes `NotFound`, `PermissionDenied`,
`AlreadyExists`, `InvalidInput`, `InvalidData`, `Interrupted`,
`WouldBlock`, `WriteZero`, `UnexpectedEof`, `BrokenPipe`, `Unsupported`,
`OutOfMemory`, and `Other`. Platform-specific codes remain observable but
must not change portable control flow.

Low-level `read` and `write` expose partial progress. A successful zero-byte
read means EOF when the requested buffer is non-empty. A successful
zero-byte write for non-empty input becomes `WriteZero` in `write_all`.
`read_exact` reports `UnexpectedEof`. High-level retrying helpers retry
`Interrupted`; primitive operations preserve it. Text readers validate UTF-8
and return `InvalidData` rather than replacement text.

## Host authority

The detailed Edition 2026 rules are fixed by the accepted
[synchronous host I/O contract](host-io.md).

`io` is a compiler-validated standard effect identity. Only the native entry
boundary may install its host handler. Naming a user effect `io`, declaring a
same-shaped operation, or forging a file value grants no authority.

The entry function may be pure or declare `with<io>`. The native launcher
handles only the validated standard `io` identity and converts an unhandled
entry failure into a deterministic nonzero exit. Library functions remain
effect-polymorphic where possible and acquire no host authority by import.

Standard streams and process arguments are link-time capabilities of the
entry environment. Open files are owned runtime capabilities: opening
requires `io`, and the resulting unforgeable `File` limits subsequent
operations to that resource. Paths never imply ambient access by themselves.

The first host implementation supports:

- `x86_64-unknown-linux-gnu`, continuously tested in CI;
- `aarch64-apple-darwin`, verified by native release testing.

The compiler is currently 64-bit-native-only. `library/std` must be rejected
with a target-specific diagnostic on Windows, WASI, 32-bit, and other targets
until that target has a runtime implementation and conformance suite.
`core` checking remains independent of host availability; `alloc` additionally
requires its allocator ABI.

## Minimum API matrix

Names below are the minimum acceptance surface. Every operation keeps its
canonical identity in the layer and definition module that owns it.

| Area | Required surface |
| --- | --- |
| `Option<T>` | `is_some`, `is_none`, `as_ref`, `as_ref<mut>`, `map`, `and_then`, `unwrap_or`, `unwrap_or_else`, `ok_or` |
| `Result<E><T>` | `is_ok`, `is_err`, `as_ref`, `as_ref<mut>`, `map`, `map_error`, `and_then`, `unwrap_or`, `unwrap_or_else`, `ok`, `err` |
| integers | `min`, `max`, `clamp`, sign queries, checked width conversions, decimal parse, decimal display |
| `str` | byte `len`, `is_empty`, `as_bytes`, equality, boundary check, checked slice, prefix/suffix, find, byte iteration, scalar iteration |
| `UnicodeScalar` | checked construction from `u32`, `to_u32`, UTF-8 encoded length, encode into caller storage |
| `String` | `new`, capacity construction, `from_str`, `from_utf8`, `as_str`, `push`, `push_str`, truncate at boundary, search, clear, byte recovery |
| `Array<T><n>` | `len`, `is_empty`, `get`, `at`, `first`, `last`, shared/mutable slice, shared/mutable iteration, swap, reverse, copy/fill where bounded |
| `Slice<T>` | the same non-owning access and iteration vocabulary as arrays, plus checked subslicing |
| `Vec<T>` | array/slice vocabulary where applicable, capacity, push/pop, insert/remove, append, truncate, extend from slice, consuming iteration |
| iteration | `find`, `position`, `contains`, `any`, `all`, and `fold`, with early-exit cleanup and forwarded effects |
| formatting | `parse`, `display`, `debug`, byte/text `writer`, `string_writer`, and allocation-backed `to_string` |
| byte I/O | `reader.read`, `reader.read_exact`, `writer.write`, `writer.write_all`, and `writer.flush` |
| console | stdin read/read_line, stdout/stderr write/print/println, and explicit flush |
| process | borrowed or owned argument iteration with defined invalid-host-text behavior |
| filesystem | `OpenOptions`, `File.open`, `File.read`, `File.write`, `File.flush`, `File.seek`, `File.close`, and bounded whole-file helpers |
| tests | `fail`, `assert`, `assert_eq`, `assert_ne`, and common `Option`/`Result` expectations |

Whole-file and read-to-end helpers take an explicit maximum byte count. They
must not allocate without a caller-visible bound. `print` and assertion
formatting use source-backed static dispatch; this milestone adds neither
reflection nor formatting macros.

## Conformance

Each public operation needs:

- a declaration-level summary, complexity, ownership mode, effects, errors,
  and traps;
- positive native coverage and rejection coverage for invalid ownership,
  borrowing, effects, or target use;
- exact boundary tests for empty input, maximum integer widths, UTF-8
  boundaries, partial I/O, interruption, early exit, and cleanup;
- deterministic output and diagnostics independent of checkout path;
- no allocator leaks or double cleanup in success, error, and early-exit paths.

The completed milestone is evidenced by the multi-module
`examples/inventory` command and native acceptance suites using the public
surface without private runtime helpers.

## Research basis

This contract combines Salicin's typed effect rows with capability-oriented
host resources. WASI distinguishes link-time function capabilities from
unforgeable runtime handles; Salicin similarly treats the entry environment
as the source of `io` authority and each opened file as a narrower owned
capability. Recent effect research compares row, capability, and modal
systems rather than requiring one representation to serve all three roles.

The byte-I/O rules follow the established separation between partial
`read`/`write` primitives and retrying exact/all helpers. Text follows the
Unicode definition of UTF-8 as one-to-four bytes per Unicode scalar value and
does not promise grapheme, normalization, locale, or collation behavior.
Naming follows clarity-at-use-site guidance and Salicin's category-aware
`PascalCase`/`snake_case` policy.

Primary references reviewed on 2026-07-27:

- [WASI capabilities](https://github.com/WebAssembly/WASI/blob/main/docs/Capabilities.md)
- [WASI repository and Preview 2 status](https://github.com/WebAssembly/WASI)
- [Rust `std::io::Write`](https://doc.rust-lang.org/std/io/trait.Write.html)
- [Unicode 17, Chapter 3](https://www.unicode.org/versions/Unicode17.0.0/core-spec/chapter-3/)
- [Swift API Design Guidelines](https://www.swift.org/documentation/api-design-guidelines/)
- [Rows and Capabilities as Modal Effects (HOPE 2025)](https://conf.researchr.org/details/icfp-splash-2025/hope-2025-papers/5/Rows-and-Capabilities-as-Modal-Effects-Extended-Abstract)
- [Zero-Overhead Lexical Effect Handlers (OOPSLA 2025)](https://cs.uwaterloo.ca/~yizhou/papers/zero-oopsla2025.pdf)

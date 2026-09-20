# Control-Flow Contracts

This document records the source contracts and compiler obligations behind Salicin control flow.
Surface semantics are normative in the [language specification](specification.md); concrete syntax
is defined by the [grammar](grammar.md).

## Identity and Authority

Standard control operations are declared in `core.control`. The compiler recognizes a declaration
only after validating its canonical lang-item identity and exact signature. A user function named
`if`, `match`, or `loop` remains an ordinary function and gains no control-flow authority.

The parser uses contextual productions to distinguish dedicated control syntax and pattern payloads
from standalone brace closures. Control contracts retain actual Brace runtime groups.
Name resolution and type checking still bind the canonical source declaration before privileged
lowering occurs.

## Lazy Calls

Ordinary braces create closures rather than generic eager blocks. Branch and loop bodies are
callable arguments that their dedicated control forms immediately use, invoking them lazily where
the contract requires it. Conditions are eager where their source order requires it.

Conceptually, `if` has this shape:

```sc fragment
let if = <e: effects, T: type> with<e>
  (condition: bool)
  {move then: with<e>(): T}
  {move else: with<e>(): T}: T
```

The ordinary surface form:

```sc fragment
if(condition) {
  left()
} else: {
  right()
}
```

must therefore preserve these properties:

- evaluate `condition` once;
- invoke exactly one branch;
- preserve the selected branch's effects;
- clean captures of the unselected branch exactly once;
- produce one common result type, allowing `never` coercion.

`while(condition) { ... }` evaluates its condition before each iteration.

When a `for` iterable itself ends in a Brace pattern body, parenthesize that
application to separate it from the loop body: `for (make { x -> x }) { (item) => ... }`.
`do { ... } while: { condition }` evaluates its condition after each iteration.
These and `if(condition) { ... } else: { ... }` are the sole spellings; the
language has no unlabeled-condition or named-closure aliases. `loop` has the
type selected by its reachable `break` values.

## Exits

`return`, `break`, and `continue` are contextual control operations with lexical targets.

- `return()` or `return(value)` exits the nearest named function or closure.
- `break()` or `break(value)` exits the nearest loop.
- `continue()` starts the next iteration of the nearest loop.

The empty forms carry `()`. No bare-operand spelling is valid for either exit.

Each exit has type `never`. Lowering must run cleanup for every initialized value whose scope is
left, without dropping transferred values or running cleanup twice.

## Deferred Actions

`defer { action }` registers `action` in the current lexical scope. The declared Brace body is captured at the
registration point and invoked only when that scope exits. Multiple actions run last-in,
first-out.

The enclosing closure-body result, return value, break value, or thrown error is evaluated before deferred actions
begin. Deferred actions therefore cannot change the selected exit value. A `continue` runs actions
registered in the iteration body before starting the next iteration; a break or continue belonging
to a nested loop does not exit an enclosing lexical scope outside that loop.

`defer` is valid only as a standalone statement. Its action has type `with<e>(): ()`, so ordinary
effect checking and handler selection apply to the invocation. Lowering must preserve the action's
capture ownership and must not expose compiler-generated binding names in diagnostics.

## Pattern Closures and Cases

A match case maps a successful pattern and guard to an arm result. It consists of:

- a pattern;
- an optional guard;
- a body.

A single pattern closure may still be written as
`{ pattern [if guard] -> expression }` and passed as one closure argument. Its
call returns `core.control.Attempt<Input><Output>`, preserving the input in
`Miss` when the pattern or guard fails.

Failure to match is not an error result and does not consume the scrutinee. The next case receives
the same logical input state. A successful pattern establishes its bindings before the guard. A
false guard rolls back those bindings and proceeds to the next case.

The compiler may represent cases internally without allocating runtime closure objects. That
optimization must preserve ordinary ownership, borrowing, effect, and cleanup semantics.

## Match

`match` evaluates its scrutinee once and tests cases in source order:

```sc fragment
match(option) {
  Some(value) if value > 0 => value,
  Some(_) => 0,
  None => -1,
}
```

This syntax maps directly to match semantics. The brace contains
comma-separated match arms; it is not a tight brace call, a closure-valued
intermediate, or a sequence of postfix match cases.

The old consecutive pattern-closure form
`callee { P -> ... } { Q -> ... }` has been removed. The replacement for
ordered alternatives is one multi-arm
`match(value) { P => ..., Q => ... }` expression.

Lowering must preserve:

- exhaustiveness over closed types;
- source-order guard evaluation;
- no reevaluation of the scrutinee;
- no move from failed alternatives;
- arm-local binding scope;
- one result type;
- exactly-once cleanup on selection, exit, or failure.

Compiler-generated internal match names must never appear in user diagnostics.

## For

`for pattern in iterable { body }` is governed by `IntoIterator` and `Iterator`:

```sc fragment
let Iterator = trait {
  let Item = <r: region>: type
  let next = <r: region>(self: Borrow<mut><r><self>): core.Option<Item<r>>
}
```

The iterable is evaluated once and converted once. Each iteration mutably borrows the iterator for
one `next` call. A yielded value is matched against the loop pattern before the body runs.

The lowering must:

- keep the iterator alive for the loop;
- bound borrow-yielding items by the receiver region;
- reject overlapping mutable yields;
- reject escaped local yields;
- drop the iterator exactly once on exhaustion, `break`, `return`, or an effect exit;
- preserve cleanup of unconsumed owned elements.

## Lowering Obligations

Privileged control lowering is valid only when it is observationally equivalent to the validated
source contract. In particular, it must preserve:

1. left-to-right evaluation;
2. single evaluation of eager operands and places;
3. lazy execution of branch and loop bodies;
4. lexical exit targets;
5. effect-row forwarding;
6. ownership and region constraints;
7. deterministic, exactly-once cleanup;
8. source locations and source-level diagnostic names.

No control construct may rely on a hidden runtime dictionary or an unvalidated spelling.

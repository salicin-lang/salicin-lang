# Salicin Grammar

Status: evolving parser reference

This document describes Salicin's concrete syntax. The
[language specification](specification.md) defines semantics. The notation is EBNF-like:

```text
"token"     literal token
TOKEN       lexical token class
[ x ]       optional
{ x }       zero or more
x | y       alternative
( x )       grouping
```

Contextual words are represented as `IDENT` by the reference lexer and interpreted by the parser
only in the corresponding position. This set includes compile-time sorts, passing modes, borrow
forms, and control-operation names.

## 1. Lexical Grammar

```ebnf
IDENT   = Unicode_XID_Start, { Unicode_XID_Continue } ;
REGION  = "'", Unicode_XID_Start, { Unicode_XID_Continue } ;
INTEGER = decimal_integer | hex_integer | octal_integer | binary_integer ;
FLOAT   = decimal_float ;
CHAR    = "'", char_content, "'" ;
STRING  = '"', { string_content }, '"' ;

line_comment  = "//", { any_except_newline } ;
block_comment = "/*", { text | block_comment }, "*/" ;
```

Names are normalized to NFC. `_` may separate digits inside a numeric literal. A `REGION` and a
character literal are distinguished by the closing quote.

The lexer emits `NEWLINE` except:

- inside unmatched `(...)` or `[...]`;
- after a token that necessarily continues an expression, including an infix or prefix operator,
  comma, `.`, `?.`, `=`, `=>`, or `:`.

Braces do not suppress newlines.

```ebnf
separator  = NEWLINE | ";" ;
separators = { separator } ;
```

## 2. Source Files and Items

```ebnf
source_file = separators, { item, separators }, EOF ;

item = [ visibility ], ( let_decl | extend_decl )
     | test_registration ;

visibility = "pub", [ "(", "package", ")" ] ;

test_registration =
    contextual("test"), "(", STRING, ")", zero_parameter_callable ;
```

A test registration cannot have an attribute or visibility. Its string must be
non-empty, and the Brace group is the test body. `test` remains an ordinary
identifier outside this top-level form. The edition-owned
`pub let test = { <name: String>{move body: with<core.error.throwing<core.string.String>>(): ()}: () => builtin() }`
declaration validates the static name and body contract.

### 2.0.1 Declaration and guard forms

These three spellings occupy different grammatical categories:

- `test("name") { ... }` is a declaration form backed by the source-visible
  `core.test` contract above. Its metadata name is consumed by syntax and its
  body has type
  `with<core.error.throwing<core.string.String>>(): ()`.
- `extend(pattern, ...)<requires: condition> { ... }` is an implementation
  declaration. Its optional `requires:` entry is an ordinary angle
  compile-time group; `extend` itself has no fake function declaration in
  `core`.
- `requires(goals) expression` is an initializer guard. It constrains the
  function body through the source-visible `core.requires` contract, passing
  the compile-time `bool` and delayed body closure.

Trait inheritance uses the same labeled `<requires: condition>` compile-time
group as `extend`; it does not invoke the function-body
guard contract.

### 2.1 Let Declarations

```ebnf
let_decl = "let", [ contextual("mut") ], IDENT,
           ( ":", type_expr, "=", expression
           | "=", declaration_rhs ) ;

declaration_rhs =
    callable_literal
  | { compile_parameter_group }, initializer
  | expression ;

callable_literal =
    "{", separators,
    callable_signature,
    [ "=>", callable_body ],
    separators, "}" ;

callable_signature =
    { compile_parameter_group },
    [ with_clause ],
    { runtime_parameter_group },
    [ "...", type_expr ],
    [ ":", declaration_annotation ],
    [ where_clause ] ;

callable_body = block_contents ;

declaration_annotation =
    type_expr
  | contextual("type")
  | contextual("sort")
  | constructor_sort ;

initializer =
    builtin_initializer
  | foreign_initializer
  | effect_decl
  | sort_decl
  | struct_decl
  | enum_decl
  | trait_decl ;

builtin_initializer =
    contextual("builtin"), "(", ")" ;

foreign_initializer =
    contextual("foreign"), "(",
    contextual("c"), [ ",", STRING ],
    ")" ;
```

`let name: type` declares an opaque nominal type. Compiler-owned sources may declare an abstract
sort with `let name: sort<2>`; user sources must declare finite sorts. The
edition static-sort registry, rather than a source declaration or open-ended
name lookup, determines which compiler-owned fragment classifiers are valid.
`let name = sort<1> { ... }` declares a sort with a known member set. Bare `sort`, `= type`,
and `= type { ... }` are not productions.
`let Name = { field: Type, ... }` is a bodyless Brace schema declaration; it introduces a nominal
struct and the same-named Brace constructor.

`builtin()` is a complete initializer available only to the embedded `core`
package. It may define a compiler-owned function, type, type constructor, or
extension method whose exact declaration is validated by the edition
contract. It is not an expression initializer available to user packages.
Every callable value has one outer brace pair. After its signature groups and
result annotation, an implementation must begin with `=>`; the remainder of
the outer braces is its body. A callable literal with no `=>` is a bodyless
contract and is valid only where an abstract callable requirement is allowed.
The removed bare form `let name = (parameters): Result => body` is not grammar.

### 2.2 Compile-Time Parameters

```ebnf
compile_parameter_group =
    "<", compile_parameter,
    { ",", compile_parameter }, [ "," ], ">" ;

compile_parameter =
    [ "..." ], compile_parameter_name, ":", compile_parameter_sort,
    [ "=", compile_parameter_default ] ;

compile_parameter_name = IDENT | REGION ;

compile_parameter_sort =
    contextual("type")
  | contextual("usize")
  | contextual("sort"), "<", ( INTEGER | IDENT ), ">"
  | contextual("region")
  | contextual("effect")
  | contextual("effects")
  | contextual("parameters")
  | contextual("constraint")
  | IDENT
  | constructor_sort ;

constructor_sort =
    constructor_sort_group, { constructor_sort_group },
    ":", ( contextual("type") | contextual("effect") | contextual("parameters") ) ;

constructor_sort_group =
    "<", constructor_sort_parameter,
    { ",", constructor_sort_parameter }, [ "," ], ">" ;

constructor_sort_parameter =
    IDENT, ":", compile_parameter_sort ;
```

`constraint` classifies normalized compiler-produced solver goals. It cannot
have a default, be supplied as an explicit source argument, or occur as a
runtime type.

Angle brackets exclusively declare compile-time groups. Parentheses, square
brackets, and braces declare runtime groups. A group therefore has exactly one
stage; compile-time and runtime parameters cannot be mixed in one group.

### 2.3 Runtime Parameters

```ebnf
runtime_parameter_group =
    runtime_delimited_group(runtime_parameter) ;

declaration_group =
    compile_parameter_group | runtime_parameter_group ;

runtime_parameter =
    { parameter_modifier },
    [ IDENT ],
    ( IDENT | "_" ),
    ":",
    type_expr ;

parameter_modifier =
    contextual("copy")
  | contextual("move")
  | contextual("mut")
  | contextual("shared")
  | IDENT ;
```

The optional first `IDENT` is an external argument label when followed by a second parameter name.
Parameter modifiers are resolved against the source-backed passing declarations.

### 2.4 Data, Effects, and Traits

```ebnf
sort_decl =
    contextual("sort"), "{", separators,
    { sort_member, separators }, "}" ;

sort_member = IDENT | contextual_word ;

effect_decl =
    contextual("effect"), "{", separators,
    { effect_operation, separators }, "}" ;

effect_operation =
    IDENT, ":",
    [ with_clause ],
    runtime_parameter_group, { runtime_parameter_group },
    ":", type_expr ;

struct_decl =
    "struct", [ struct_options ], "{", separators,
    { [ visibility ], IDENT, ":", type_expr, [ "," ], separators },
    "}" ;

struct_options =
    "(", struct_option, { ",", struct_option }, [ "," ], ")" ;

struct_option =
    contextual("c")
  | contextual("derive"), ":", IDENT ;

enum_decl =
    "enum", "{", separators,
    { enum_variant, [ "," ], separators },
    "}" ;

enum_variant =
    IDENT,
    [ "(", [ type_expr, { ",", type_expr }, [ "," ] ], ")"
    | "{", [ named_field, { ",", named_field }, [ "," ] ], "}" ] ;

named_field = [ visibility ], IDENT, ":", type_expr ;

trait_decl =
    "trait",
    [ "<", self_parameter, ">" ],
    [ requires_compile_group ],
    "{", separators,
    { trait_member, separators },
    "}" ;

self_parameter = contextual("self"), ":", compile_parameter_sort ;

trait_member =
    IDENT, ":", callable_signature, [ "=", callable_body ]
  | IDENT, ":", ( contextual("type") | contextual("parameters") )
  | IDENT, ":", compile_parameter_group, { compile_parameter_group },
    ":", ( contextual("type") | contextual("parameters") ) ;
```

Trait callable members use declarations of the form `name: signature`:
the bodyless form declares an abstract requirement and `name: signature = body`
for a default implementation. Associated declarations also omit `let`.

Effect operations use the same `name: signature` form: they omit `let` and `=`,
require an explicit runtime group, and have no implementation body. Their names
are used by qualified operation calls and handler arms. `Return` is reserved for
handler completion.

An associated type or associated constructor has no runtime parameter groups. Its compile-time
groups appear before `: type`. Associated declaration defaults are not supported yet.

### 2.5 Extensions and Predicates

```ebnf
extend_decl =
    "extend", "(",
    type_expr,
    [ ",", trait_ref ],
    ")",
    [ requires_compile_group ],
    "{", separators,
    { extend_member, separators },
    "}" ;

extend_member =
    "let", IDENT, "=", callable_literal
  | "let", IDENT, "=", expression ;

constraint_guard =
    contextual("requires"), constraint_arguments ;

requires_compile_group =
    "<", contextual("requires"), ":",
    constraint_expression,
    { ( "&&" | "," ), constraint_expression },
    [ "," ], ">" ;

constraint_arguments =
    "(", constraint_expression,
    { ( "&&" | "," ), constraint_expression },
    [ "," ], ")" ;

constraint_expression =
    type_path, contextual("is"), trait_ref
  | projection, "==", type_expr ;

projection =
    type_path, ".", IDENT, { compile_parameter_group } ;

trait_ref =
    path,
    [ type_argument_group ] ;

trait_argument = [ IDENT, ":" ], type_expr ;
```

The `requires:` group in a trait or extension header is an ordinary labeled
angle compile-time group carrying a boolean requirement, not dedicated
parenthesized syntax, a callable declaration, or a new static sort. `extend`
is parser-owned declaration syntax and has no
corresponding `extend` function or language item.

An associated type projection equality follows the trait constraint whose
evidence owns that projection. A generic associated constructor equation
declares its local binders on the projection, for example
`T is Iterator && T.Item<r: region> == Borrow<r><i32>`.

An extension requirement group is evaluated after the target pattern binds
its compile-time parameters. A function applies the same compiler-owned
`requires` guard to its body:

```sc fragment
let duplicate = { <T: type>(value: T): (T, T) requires(T is Copyable) =>
  (value, value)
}
```

Both forms lower `is` relations and projection equalities to solver goals. An
unsatisfied concrete goal is a compile-time error; an abstract goal is
retained until generic instantiation. Trait prerequisites use the same
constraint arguments directly, for example `trait<requires: self is Movable> {}`.

### 2.6 Foreign Declarations

```ebnf
foreign_function =
    "let", IDENT, "=", "{",
    runtime_parameter_group,
    ":", type_expr,
    "=>", foreign_initializer, "}" ;
```

A foreign declaration has exactly one runtime parameter group, no
compile-time parameters, explicit effects, `requires` guard, or body. Omitting
the string uses the Salicin declaration name as the linker symbol. The only
accepted ABI name is the contextual identifier `c`. Grouped `extern`
declarations and `@` attributes are not grammar productions.

### 2.7 Compiler Definitions

```ebnf
builtin_definition =
    "let", IDENT, "=", "{",
    { declaration_group },
    ":", declaration_annotation,
    "=>", builtin_initializer, "}" ;
```

The core-private bootstrap is the sole compiler definition that may omit a
result annotation; like every callable value, its signature and initializer
remain inside outer braces.
Every other marker must match a known
compiler-owned edition contract and is removed before code generation.
Trait callable requirements, effect operations, and user opaque types remain
bodyless declarations rather than builtin definitions. The callable forms are
introduced by a colon after the member or operation name.

The root `core` module also contains the public overloads
`pub let foreign = { <abi: abi>: never => builtin() }` and
`pub let foreign = { <abi: abi, symbol: String>: never => builtin() }`, plus
`pub let test = { <name: String>{move body: with<core.error.throwing<core.string.String>>(): ()}: () => builtin() }`
and the generic `requires(condition, body)` contract. They authorize the
`foreign(c, ...)` initializer, top-level test registration, and function-body
guard respectively;
`c` is a finite `abi` sort value, while linker and test-name strings remain syntax metadata.

## 3. Types

```ebnf
type_expr = effect_callable_type | function_type | postfix_type ;

effect_callable_type =
    with_clause, function_type ;

function_type =
    function_type_group,
    { function_type_group },
    ":", type_expr ;

function_type_group =
    runtime_delimited_group(function_type_parameter) ;

function_type_parameter =
    { parameter_modifier }, [ IDENT, ":" ], type_expr ;

postfix_type =
    primary_type,
    { type_argument_group } ;

primary_type =
    array_type
  | path
  | primitive_type
  | tuple_type
  | borrow_type ;

tuple_type =
    "(", type_expr, ",",
    [ type_expr, { ",", type_expr }, [ "," ] ],
    ")" ;

borrow_type =
    contextual("Borrow"),
    [ type_argument_group ],
    [ type_argument_group ],
    type_argument_group ;

array_type =
    path, "<", type_expr, ">", "<", static_usize_expression, ">" ;

static_usize_expression =
    expression ;  (* restricted semantically to the pure static subset *)

type_argument_group =
    "<", [ type_argument, { ",", type_argument }, [ "," ] ], ">" ;

type_argument = [ IDENT, ":" ], type_expr ;

with_clause =
    contextual("with"), "<",
    [ effect_ref, { ",", effect_ref }, [ "," ] ],
    ">" ;

effect_ref = path, [ type_argument_group ] ;
```

`()` is unit, while `(t,)` is a one-element tuple. Curried constructor applications retain each
argument group in the AST. The `array_type` production applies when `path` resolves to the
edition's validated `Array` type form; other constructor arguments remain type expressions.
`static_usize_expression` admits literals, static names, checked operators, and calls to eligible
ordinary pure functions.

`with<E>(a): b` applies one normalized effect row to the complete multi-group
callable `(a): b`. Callable declarations place every compile-time group,
optional effect row, runtime group, result annotation, and implementation
inside their outer braces. The final `:` introduces both a declaration's
result and a callable type's result. `=>` separates a callable signature from
the body that occupies the remainder of the outer braces. Callable types stay
unbraced `(T): R`.
Canonical presentation separates an adjacent compile-time group and effect
prefix with whitespace: `<T: type> with<e>`, not `<T: type>with<e>`.

## 4. Expressions

Precedence is listed from lowest to highest:

```ebnf
expression         = assignment ;
assignment         = propagation, [ assignment_op, assignment ] ;
propagation        = coalescing, { "!", [ "!" ] } ;
coalescing         = logical_or, { "??", logical_or } ;
logical_or         = logical_and, { "||", logical_and } ;
logical_and        = comparison, { "&&", comparison } ;
comparison         = bit_or, [ comparison_op, bit_or ] ;
bit_or             = bit_xor, { "|", bit_xor } ;
bit_xor            = bit_and, { "^", bit_and } ;
bit_and            = shift, { "&", shift } ;
shift              = additive, { shift_op, additive } ;
additive           = multiplicative, { additive_op, multiplicative } ;
multiplicative     = prefix, { multiplicative_op, prefix } ;
prefix             = { prefix_op }, postfix ;
postfix            = primary, { postfix_suffix } ;

assignment_op =
    "=" | "+=" | "-=" | "*=" | "/=" | "%="
  | "&=" | "|=" | "^=" | "<<=" | ">>=" ;

comparison_op = "==" | "!=" | "<" | "<=" | ">" | ">=" ;
shift_op = "<<" | ">>" ;
additive_op = "+" | "-" ;
multiplicative_op = "*" | "/" | "%" ;
prefix_op = "-" | "!" | contextual("move") | contextual("borrow") ;
```

```ebnf
postfix_suffix =
    handler_suffix
  | argument_group
  | ".", IDENT
  | "?.", IDENT
  | brace_application ;

argument_group =
    delimited_group(argument) ;

argument = [ IDENT, ":" ], expression ;

brace_application =
    [ horizontal_space ], "{", brace_group_contents, "}" ;

handler_suffix =
    ".", contextual("handle"),
    "(", expression, ")", horizontal_space,
    "{", separators,
    handler_arm, { ",", separators, handler_arm },
    [ "," ], separators, "}" ;

handler_arm =
    IDENT, "(", [ pattern, { ",", pattern }, [ "," ] ], ")",
    "=>", expression ;
```

Parenthesis, square, and angle postfix openers must be byte-adjacent to their
callee. Brace application permits horizontal whitespace before `{`. One
delimiter-aware call model preserves and checks every delimiter against the
corresponding declaration or function-type group. `<>` exclusively supplies a
compile-time group, including struct-constructor and effect arguments; `()`,
`[]`, and `{}` supply runtime groups. Thus `a < b` is a
comparison (comparison operators require surrounding whitespace), while
`a<b>` is an angle call. A postfix square group is the uniform surface form
for calls and retains bounds-checked indexing/place behavior when its callee
is indexable. Tight and spaced ordinary brace groups are the same Brace `DelimitedCall`.
The declaration schema resolves their contents as ordinary/labeled arguments or
as a callable parameter body. Struct construction uses this production.
Standalone brace expressions remain closures.

The handler suffix is distinct. It requires exactly one unlabeled
parenthesized action and whitespace before the arm group:
`.handle(action) { Operation(...) => expression, Return(value) => expression }`.
The action is delayed by handler semantics. Handler arms are not labeled call
arguments; `action:` and `done:` are not productions.

```ebnf
delimited_group(item) =
    "(", [ item, { ",", item }, [ "," ] ], ")"
  | "[", [ item, { ",", item }, [ "," ] ], "]"
  | "<", [ item, { ",", item }, [ "," ] ], ">"
  | "{", [ item, { ",", item }, [ "," ] ], "}" ;

runtime_delimited_group(item) =
    "(", [ item, { ",", item }, [ "," ] ], ")"
  | "[", [ item, { ",", item }, [ "," ] ], "]"
  | "{", [ item, { ",", item }, [ "," ] ], "}" ;
```

In an angle-call context, the parser splits a tight `>>` into two closing
delimiters when two angle groups are open. A whitespace-separated `a >> b`
remains the shift operator.

```ebnf
primary =
    literal
  | path
  | tuple_expression
  | array_expression
  | callable_expression
  | match_expression
  | if_expression
  | while_expression
  | do_while_expression
  | return_expression
  | break_expression
  | await_expression ;

literal = INTEGER | FLOAT | CHAR | STRING
        | contextual("true") | contextual("false") | "()" ;

tuple_expression =
    "(", expression, ",",
    [ expression, { ",", expression }, [ "," ] ],
    ")" ;

array_expression =
    "[", [ expression, { ",", expression }, [ "," ] ], "]" ;

callable_expression =
    zero_parameter_callable
  | parameterized_callable
  | pattern_callable ;

zero_parameter_callable =
    "{", block_contents, "}" ;

parameterized_callable = callable_literal ;

pattern_callable =
    "{", separators,
    pattern_callable_arm,
    { ",", separators, pattern_callable_arm },
    [ "," ], separators, "}" ;

pattern_callable_arm =
    pattern, [ contextual("if"), expression ], "=>", expression ;

```

Control operations are contextual and recognized by their dedicated validated
shapes. In particular, the grammar has no aliases for `if`, `while`, or the
post-test `do` loop, and `return`, `break`, and `await` require parentheses.

## 5. Closures and Matches

```ebnf
closure_body =
    zero_parameter_callable ;

block_contents =
    separators,
    { block_item, separators },
    [ expression, [ NEWLINE ] ] ;

block_item =
    let_decl
  | expression ;

match_expression =
    contextual("match"), "(", expression, ")", match_closure ;

match_closure =
    "{", separators,
    match_arm,
    { ",", separators, match_arm },
    [ "," ], separators,
    "}" ;

match_arm =
    pattern,
    [ contextual("if"), expression ],
    "=>",
    expression ;

if_expression =
    contextual("if"), "(", expression, ")", closure_body,
    [ contextual("else"), ":", closure_body ] ;

while_expression =
    contextual("while"), "(", expression, ")", closure_body ;

do_while_expression =
    contextual("do"), closure_body,
    contextual("while"), ":", closure_body ;

return_expression = contextual("return"), "(", [ expression ], ")" ;
break_expression = contextual("break"), "(", [ expression ], ")" ;
await_expression = contextual("await"), "(", expression, ")" ;
```

An ordinary `{ expression }` is a zero-parameter closure, not a generic eagerly
evaluated block. A parameterized callable also requires outer braces around its
complete signature and body. Function declarations bind those callable
literals; dedicated control forms consume zero-parameter closures and invoke
them at the point required by their contracts. `match(value) { ... }`
maps directly to a match expression whose comma-separated arms are stored as
match arms. Its brace is not a tight Brace `DelimitedCall`, and parsing does
not create a closure or other call intermediate.

A pattern callable has one or more comma-separated arms in one outer brace pair:
`{ Pattern [if expression] => expression, ... }`. Calling it tries arms in
source order. The former `->` arm and consecutive
`callee { P -> ... } { Q -> ... }` forms are not grammar.

`c` selects the C data representation and may appear at most once. It is
orthogonal to named options such as `derive: Copyable`; for example,
`struct(c, derive: Copyable) { ... }`. Empty option lists retain the ordinary
Salicin representation.

```ebnf
pattern =
    "_"
  | IDENT
  | literal_pattern
  | tuple_pattern
  | struct_pattern
  | variant_pattern ;

tuple_pattern =
    "(", pattern, ",",
    [ pattern, { ",", pattern }, [ "," ] ],
    ")" ;

struct_pattern =
    path, "{",
    [ field_pattern, { ",", field_pattern }, [ "," ] ],
    "}" ;

field_pattern = IDENT, [ ":", pattern ] ;

variant_pattern =
    path,
    [ "(", [ pattern, { ",", pattern }, [ "," ] ], ")"
    | "{", [ field_pattern, { ",", field_pattern }, [ "," ] ], "}" ] ;
```

Declaration bodies and pattern payloads use braces in their dedicated
productions. In expression position, an uncalled brace is a closure; a tight
postfix brace is a Brace `DelimitedCall`, with struct construction determined only
after its callee resolves.

## 6. Paths

```ebnf
path =
    [ contextual("self") | "super" | "root" | IDENT ],
    { ".", IDENT } ;
```

The resolver, not the parser, determines whether the first segment names the current package, a
dependency package, or an entity in lexical scope.

## 7. Required Ambiguity Tests

The parser test suite must lock down at least these cases:

```sc fragment
let unit = ()
let singleton = (value,)
let grouped = (value)

f
(x)

let curried = make<T>(value)
let field = value.member
let chained = value?.member

if(condition) { left() } else: { right() }

match(value) {
  Some(item) => item,
  None => fallback,
}
```

These examples distinguish unit from tuples, grouping from tuple syntax, a new statement from a
continued call, compile-time from runtime application, member access from conditional chaining,
and closures from payload braces.

use super::*;

fn core_source_with_copy(copy_declaration: &str) -> String {
    [
        r#"
pub let Option<T: type> = enum { Some(T), None }
pub let Result<Error: type><T: type> = enum { Ok(T), Err(Error) }
pub let never = enum {}
pub let Movable = trait {}
"#,
        copy_declaration,
        r#"
pub let Droppable = trait {
  drop(self: Borrow<mut><self>)(): ()
}
pub let Add<Rhs: type> = trait {
  Output: type
  add(self)(rhs: Rhs): Output
}
pub let Sub<Rhs: type> = trait {
  Output: type
  sub(self)(rhs: Rhs): Output
}
pub let Mul<Rhs: type> = trait {
  Output: type
  mul(self)(rhs: Rhs): Output
}
pub let Div<Rhs: type> = trait {
  Output: type
  div(self)(rhs: Rhs): Output
}
pub let Rem<Rhs: type> = trait {
  Output: type
  rem(self)(rhs: Rhs): Output
}
pub let Eq<Rhs: type> = trait {
  eq(self: Borrow<self>)(rhs: Borrow<Rhs>): bool
}
pub let PartialOrdering = enum { Less, Equal, Greater, Unordered }
pub let PartialOrd<Rhs: type> = trait {
  partial_cmp(self: Borrow<self>)(rhs: Borrow<Rhs>): PartialOrdering
}
pub let Neg = trait {
  Output: type
  neg(self)(): Output
}
pub let Not = trait {
  Output: type
  not(self)(): Output
}
pub let BitAnd<Rhs: type> = trait {
  Output: type
  bit_and(self)(rhs: Rhs): Output
}
pub let BitOr<Rhs: type> = trait {
  Output: type
  bit_or(self)(rhs: Rhs): Output
}
pub let BitXor<Rhs: type> = trait {
  Output: type
  bit_xor(self)(rhs: Rhs): Output
}
pub let Shl<Rhs: type> = trait {
  Output: type
  shl(self)(rhs: Rhs): Output
}
pub let Shr<Rhs: type> = trait {
  Output: type
  shr(self)(rhs: Rhs): Output
}
pub let Index<Key: type> = trait {
  Output: type
  index<a: access>(self: Borrow<a><self>)(key: Key): Borrow<a><Output>
}
pub let str: type = builtin()
"#,
    ]
    .concat()
}

fn core_bundle_from_source(source: &str) -> Result<CoreBundle, CoreBundleError> {
    let source = format!(
        "{source}\n{TEST_ASSIGNMENT_OPS}\n{TEST_CHAIN_OPS}\n{EDITION_2026_EFFECT}\n{EDITION_2026_ERROR}\n{EDITION_2026_UNSAFE}\n{EDITION_2026_ASYNC}\n{EDITION_2026_PRIMITIVES}\n{EDITION_2026_SORTS}\n{EDITION_2026_FOREIGN}\n{EDITION_2026_PASSING}\n{EDITION_2026_BORROW}\n{EDITION_2026_CONTROL}\n{EDITION_2026_ITER}\n{EDITION_2026_MEMORY}\nlet builtin(): never = builtin()\npub let test<name: String>{{move body: with<core.error.throwing<core.string.String>>() :()}}: () = builtin()\npub let requires<condition: bool, e: effects, Result: type>:with<e>{{move body: with<e>() :Result}}: Result = builtin()"
    );
    let mut program = parser::parse(&source).map_err(|error| {
        CoreBundleError::new(
            Edition::Edition2026,
            vec![format!("embedded prelude does not parse: {error}")],
        )
    })?;
    for origin in &mut program.item_origins {
        origin.package = PackageId::CORE.0;
        origin.module_path = vec!["@core".to_owned()];
        if let Some(location) = &mut origin.source {
            location.path = Some("<core:test>".to_owned());
        }
    }
    let lang_items = validate_program(Edition::Edition2026, &program)?;
    Ok(CoreBundle {
        edition: Edition::Edition2026,
        program,
        lang_items,
    })
}

fn edition_2026_test_modules<'a>(overrides: &[(&str, &'a str)]) -> Vec<(&'static str, &'a str)> {
    let mut modules = vec![
        ("lib", EDITION_2026_LIB),
        ("prelude", EDITION_2026_PRELUDE),
        ("primitives", EDITION_2026_PRIMITIVES),
        ("never", EDITION_2026_NEVER),
        ("marker", EDITION_2026_MARKER),
        ("option", EDITION_2026_OPTION),
        ("result", EDITION_2026_RESULT),
        ("error", EDITION_2026_ERROR),
        ("cmp", EDITION_2026_CMP),
        ("flow", EDITION_2026_FLOW),
        ("ops", EDITION_2026_OPS),
        ("ops/arith", EDITION_2026_OPS_ARITH),
        ("ops/bit", EDITION_2026_OPS_BIT),
        ("ops/assign", EDITION_2026_OPS_ASSIGN),
        ("ops/index", EDITION_2026_OPS_INDEX),
        ("effect", EDITION_2026_EFFECT),
        ("unsafe", EDITION_2026_UNSAFE),
        ("async", EDITION_2026_ASYNC),
        ("sorts", EDITION_2026_SORTS),
        ("foreign", EDITION_2026_FOREIGN),
        ("passing", EDITION_2026_PASSING),
        ("borrow", EDITION_2026_BORROW),
        ("control", EDITION_2026_CONTROL),
        ("iter", EDITION_2026_ITER),
        ("memory", EDITION_2026_MEMORY),
        ("numeric", EDITION_2026_NUMERIC),
        ("string", EDITION_2026_STRING),
        ("literal", EDITION_2026_LITERAL),
        ("fmt", EDITION_2026_FMT),
        ("testing", EDITION_2026_TESTING),
    ];
    for (module, source) in overrides {
        let Some((_, target)) = modules
            .iter_mut()
            .find(|(candidate, _)| candidate == module)
        else {
            panic!("unknown edition 2026 core test module `{module}`");
        };
        *target = *source;
    }
    modules
}

#[test]
fn edition_2026_bundle_parses_and_validates() {
    let bundle = CoreBundle::for_edition(Edition::Edition2026).unwrap();

    assert_eq!(bundle.edition(), Edition::Edition2026);
    assert_eq!(bundle.program().items.len(), LangItemKind::ALL.len() + 421);
    for kind in LangItemKind::ALL {
        let lang_item = bundle.lang_items().get(kind);
        assert_eq!(lang_item.kind(), kind);
        let canonical = match kind {
            LangItemKind::Builtin | LangItemKind::Test | LangItemKind::Requires => {
                format!("core::{}", kind.source_name())
            }
            LangItemKind::Foreign => "core::foreign::foreign".to_owned(),
            LangItemKind::Option => "core::option::Option".to_owned(),
            LangItemKind::Result => "core::result::Result".to_owned(),
            LangItemKind::Never => "core::never::never".to_owned(),
            LangItemKind::Bool
            | LangItemKind::I8
            | LangItemKind::I16
            | LangItemKind::I32
            | LangItemKind::I64
            | LangItemKind::I128
            | LangItemKind::ISize
            | LangItemKind::U8
            | LangItemKind::U16
            | LangItemKind::U32
            | LangItemKind::U64
            | LangItemKind::U128
            | LangItemKind::USize => {
                format!("core::primitives::{}", kind.source_name())
            }
            LangItemKind::Move | LangItemKind::Copy | LangItemKind::Drop => {
                format!("core::marker::{}", kind.source_name())
            }
            LangItemKind::Poll
            | LangItemKind::Future
            | LangItemKind::Executor
            | LangItemKind::AsyncFunction
            | LangItemKind::AwaitFunction => {
                format!("core::async::{}", kind.source_name())
            }
            LangItemKind::Add
            | LangItemKind::Sub
            | LangItemKind::Mul
            | LangItemKind::Div
            | LangItemKind::Rem
            | LangItemKind::Neg => format!("core::ops::arith::{}", kind.source_name()),
            LangItemKind::BitAnd
            | LangItemKind::BitOr
            | LangItemKind::BitXor
            | LangItemKind::Shl
            | LangItemKind::Shr
            | LangItemKind::Not => format!("core::ops::bit::{}", kind.source_name()),
            LangItemKind::AddAssign
            | LangItemKind::SubAssign
            | LangItemKind::MulAssign
            | LangItemKind::DivAssign
            | LangItemKind::RemAssign
            | LangItemKind::BitAndAssign
            | LangItemKind::BitOrAssign
            | LangItemKind::BitXorAssign
            | LangItemKind::ShlAssign
            | LangItemKind::ShrAssign => {
                format!("core::ops::assign::{}", kind.source_name())
            }
            LangItemKind::Eq | LangItemKind::PartialOrdering | LangItemKind::PartialOrd => {
                format!("core::cmp::{}", kind.source_name())
            }
            LangItemKind::Index => "core::ops::index::Index".to_owned(),
            LangItemKind::Chain
            | LangItemKind::Coalesce
            | LangItemKind::Unwrap
            | LangItemKind::Raise => {
                format!("core::flow::{}", kind.source_name())
            }
            LangItemKind::UnsafeEffect => "core::unsafe::unsafety".to_owned(),
            LangItemKind::ThrowsEffect => "core::error::throwing".to_owned(),
            LangItemKind::AsyncEffect => "core::async::suspension".to_owned(),
            LangItemKind::TypeSort
            | LangItemKind::RegionSort
            | LangItemKind::EffectSort
            | LangItemKind::EffectsSort
            | LangItemKind::ParametersSort => {
                format!("core::sorts::{}", kind.source_name())
            }
            LangItemKind::AbiSort => "core::foreign::abi".to_owned(),
            LangItemKind::CopyParameters | LangItemKind::MoveParameters => {
                format!("core::passing::{}", kind.source_name())
            }
            LangItemKind::AccessSort => {
                format!("core::borrow::{}", kind.source_name())
            }
            LangItemKind::BorrowTypeForm | LangItemKind::BorrowValueForm => {
                format!("core::borrow::{}", kind.source_name())
            }
            LangItemKind::ArrayTypeForm
            | LangItemKind::SliceTypeForm
            | LangItemKind::PtrTypeForm
            | LangItemKind::PtrValueForm
            | LangItemKind::SizeOf
            | LangItemKind::AlignOf => {
                format!("core::memory::{}", kind.source_name())
            }
            LangItemKind::StrTypeForm => "core::string::str".to_owned(),
            LangItemKind::Continuation | LangItemKind::EffectCallable | LangItemKind::Handle => {
                format!("core::effect::{}", kind.source_name())
            }
            LangItemKind::Attempt
            | LangItemKind::BreakEffect
            | LangItemKind::ContinueEffect
            | LangItemKind::ReturnEffect
            | LangItemKind::Break
            | LangItemKind::BreakUnit
            | LangItemKind::Continue
            | LangItemKind::Return
            | LangItemKind::ReturnUnit
            | LangItemKind::Do
            | LangItemKind::DoWhile
            | LangItemKind::Loop
            | LangItemKind::While
            | LangItemKind::If
            | LangItemKind::Match
            | LangItemKind::For
            | LangItemKind::Defer => format!("core::control::{}", kind.source_name()),
            LangItemKind::Try | LangItemKind::Throw => {
                format!("core::error::{}", kind.source_name())
            }
            LangItemKind::Unsafe => "core::unsafe::unsafe".to_owned(),
            LangItemKind::Iterator | LangItemKind::IntoIterator => {
                format!("core::iter::{}", kind.source_name())
            }
        };
        assert_eq!(
            item_name(&bundle.program().items[lang_item.item_index()]),
            Some(canonical.as_str())
        );
        assert_eq!(lang_item.canonical_name(), canonical.as_str());
        let module_path: Vec<&str> = match kind {
            LangItemKind::Builtin | LangItemKind::Test | LangItemKind::Requires => vec![],
            LangItemKind::Foreign | LangItemKind::AbiSort => vec!["foreign"],
            LangItemKind::Option => vec!["option"],
            LangItemKind::Result => vec!["result"],
            LangItemKind::Never => vec!["never"],
            LangItemKind::Bool
            | LangItemKind::I8
            | LangItemKind::I16
            | LangItemKind::I32
            | LangItemKind::I64
            | LangItemKind::I128
            | LangItemKind::ISize
            | LangItemKind::U8
            | LangItemKind::U16
            | LangItemKind::U32
            | LangItemKind::U64
            | LangItemKind::U128
            | LangItemKind::USize => vec!["primitives"],
            LangItemKind::Move | LangItemKind::Copy | LangItemKind::Drop => vec!["marker"],
            LangItemKind::Poll
            | LangItemKind::Future
            | LangItemKind::Executor
            | LangItemKind::AsyncFunction
            | LangItemKind::AwaitFunction => vec!["async"],
            LangItemKind::Add
            | LangItemKind::Sub
            | LangItemKind::Mul
            | LangItemKind::Div
            | LangItemKind::Rem
            | LangItemKind::Neg => vec!["ops", "arith"],
            LangItemKind::BitAnd
            | LangItemKind::BitOr
            | LangItemKind::BitXor
            | LangItemKind::Shl
            | LangItemKind::Shr
            | LangItemKind::Not => vec!["ops", "bit"],
            LangItemKind::AddAssign
            | LangItemKind::SubAssign
            | LangItemKind::MulAssign
            | LangItemKind::DivAssign
            | LangItemKind::RemAssign
            | LangItemKind::BitAndAssign
            | LangItemKind::BitOrAssign
            | LangItemKind::BitXorAssign
            | LangItemKind::ShlAssign
            | LangItemKind::ShrAssign => vec!["ops", "assign"],
            LangItemKind::Eq | LangItemKind::PartialOrdering | LangItemKind::PartialOrd => {
                vec!["cmp"]
            }
            LangItemKind::Index => vec!["ops", "index"],
            LangItemKind::Chain
            | LangItemKind::Coalesce
            | LangItemKind::Unwrap
            | LangItemKind::Raise => vec!["flow"],
            LangItemKind::UnsafeEffect => vec!["unsafe"],
            LangItemKind::ThrowsEffect => vec!["error"],
            LangItemKind::AsyncEffect => vec!["async"],
            LangItemKind::TypeSort
            | LangItemKind::RegionSort
            | LangItemKind::EffectSort
            | LangItemKind::EffectsSort
            | LangItemKind::ParametersSort => vec!["sorts"],
            LangItemKind::CopyParameters | LangItemKind::MoveParameters => vec!["passing"],
            LangItemKind::AccessSort => vec!["borrow"],
            LangItemKind::BorrowTypeForm | LangItemKind::BorrowValueForm => vec!["borrow"],
            LangItemKind::ArrayTypeForm
            | LangItemKind::SliceTypeForm
            | LangItemKind::PtrTypeForm
            | LangItemKind::PtrValueForm
            | LangItemKind::SizeOf
            | LangItemKind::AlignOf => vec!["memory"],
            LangItemKind::StrTypeForm => vec!["string"],
            LangItemKind::Continuation | LangItemKind::EffectCallable | LangItemKind::Handle => {
                vec!["effect"]
            }
            LangItemKind::Attempt
            | LangItemKind::BreakEffect
            | LangItemKind::ContinueEffect
            | LangItemKind::ReturnEffect
            | LangItemKind::Break
            | LangItemKind::BreakUnit
            | LangItemKind::Continue
            | LangItemKind::Return
            | LangItemKind::ReturnUnit
            | LangItemKind::Do
            | LangItemKind::DoWhile
            | LangItemKind::Loop
            | LangItemKind::While
            | LangItemKind::If
            | LangItemKind::Match
            | LangItemKind::For
            | LangItemKind::Defer => vec!["control"],
            LangItemKind::Try | LangItemKind::Throw => vec!["error"],
            LangItemKind::Unsafe => vec!["unsafe"],
            LangItemKind::Iterator | LangItemKind::IntoIterator => vec!["iter"],
        };
        let mut expected_origin_path = vec!["@core".to_owned()];
        expected_origin_path.extend(module_path.into_iter().map(str::to_owned));
        let origin = &bundle.program().item_origins[lang_item.item_index()];
        assert_eq!(origin.package, PackageId::CORE.0);
        assert_eq!(origin.module_path, expected_origin_path);
        let location = origin.source.as_deref().expect("core item source location");
        assert!(location.line > 0);
        assert!(location.column > 0);
    }

    let failure = &bundle.program().items[bundle.lang_items().failure_effect().item_index()];
    let never_name = bundle.lang_items().never().canonical_name().to_owned();
    assert!(matches!(
        failure,
        Item::Effect(definition)
            if matches!(
                definition.operations.as_slice(),
                [operation]
                    if operation.name == "raise"
                        && operation.return_type == Some(Type::Named(never_name.clone(), Vec::new()))
            )
    ));
    let suspension = bundle
        .program()
        .items
        .iter()
        .find(|item| item_name(item) == Some("core::async::suspension"))
        .expect("core.async.suspension must be mounted");
    assert!(matches!(
        suspension,
        Item::Effect(definition)
            if matches!(
                definition.operations.as_slice(),
                [operation] if operation.name == "suspend" && operation.return_type == Some(Type::Unit)
            )
    ));
}

fn mixed_builtin_type_application(source: &str) -> Option<(usize, &'static str)> {
    fn angle_groups_end(source: &str) -> Option<usize> {
        let bytes = source.as_bytes();
        let mut offset = 0;
        while bytes.get(offset) == Some(&b'<') {
            let mut depth = 0usize;
            let mut closed = false;
            for (index, byte) in bytes.iter().enumerate().skip(offset) {
                match byte {
                    b'<' => depth += 1,
                    b'>' => {
                        depth = depth.checked_sub(1)?;
                        if depth == 0 {
                            offset = index + 1;
                            closed = true;
                            break;
                        }
                    }
                    _ => {}
                }
            }
            if !closed {
                return None;
            }
        }
        Some(offset)
    }

    for (line_index, line) in source.lines().enumerate() {
        let code = line.split_once("//").map_or(line, |(code, _)| code);
        for (family, label) in [
            ("Ptr", "ptr"),
            ("Borrow", "borrow"),
            ("Result", "result"),
            ("Attempt", "attempt"),
            ("Array", "array"),
        ] {
            for (offset, _) in code.match_indices(family) {
                let prefix = &code[..offset];
                let boundary = prefix
                    .as_bytes()
                    .last()
                    .is_none_or(|byte| !byte.is_ascii_alphanumeric() && *byte != b'_');
                if !boundary || !prefix.contains(':') {
                    continue;
                }
                let suffix = &code[offset + family.len()..];
                if suffix.starts_with('(') {
                    return Some((line_index + 1, label));
                }
                if suffix.starts_with('<') {
                    let Some(groups_end) = angle_groups_end(suffix) else {
                        continue;
                    };
                    if suffix[groups_end..].starts_with('(') {
                        return Some((line_index + 1, label));
                    }
                }
            }
        }
    }
    None
}

#[test]
fn edition_2026_official_sources_use_canonical_delimiters_and_type_applications() {
    let sources = crate::core::incremental_sources(Edition::Edition2026)
        .map(|(module, source)| ("core", module, source))
        .chain(
            crate::alloc::incremental_sources(Edition::Edition2026)
                .map(|(module, source)| ("alloc", module, source)),
        )
        .chain(
            crate::standard::incremental_sources(Edition::Edition2026)
                .map(|(module, source)| ("std", module, source)),
        )
        .collect::<Vec<_>>();
    assert_eq!(sources.len(), 41);

    for (layer, module, source) in sources {
        let program = parser::parse(source).unwrap_or_else(|error| {
            panic!("embedded {layer} module `{module}` does not parse: {error}")
        });
        let diagnostics = crate::standard::delimiter_diagnostics(&program, layer);
        assert!(
            diagnostics.is_empty(),
            "embedded {layer} module `{module}` has noncanonical declaration delimiters: {diagnostics:?}"
        );
        assert_eq!(
            mixed_builtin_type_application(source),
            None,
            "embedded {layer} module `{module}` has a mixed builtin type application"
        );
    }
}

#[test]
fn canonical_type_application_check_rejects_mixed_builtin_families() {
    for (source, family) in [
        ("let f = (value: Ptr<mut>(u8)): ()", "ptr"),
        ("let f = (value: Borrow<a>(t)): ()", "borrow"),
        ("let f = (): Result<Error>(())", "result"),
        ("let f = (): Attempt<Input>(Output)", "attempt"),
        ("let f = (): Array<t>(4)", "array"),
    ] {
        assert_eq!(mixed_builtin_type_application(source), Some((1, family)));
    }
}

#[test]
fn core_bundle_rejects_legacy_parenthesized_compile_groups() {
    let malformed = EDITION_2026_BORROW.replace("<a: access = shared>", "(a: access = shared)");
    assert_ne!(malformed, EDITION_2026_BORROW);

    let modules = edition_2026_test_modules(&[("borrow", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(
        error.diagnostics().iter().any(|diagnostic| {
            diagnostic.contains(
                "runtime parameter groups cannot contain compile-time binders; use `<name: sort>`",
            )
        }),
        "{:?}",
        error.diagnostics()
    );
}

#[test]
fn builtin_markers_are_explicit_and_bounded_core_contracts() {
    let missing_bootstrap = EDITION_2026_LIB.replace("let builtin(): never = builtin()\n", "");
    let modules = edition_2026_test_modules(&[("lib", &missing_bootstrap)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic == "missing lang item `builtin`"));

    for (name, declaration) in [
        (
            "test",
            "pub let test<name: String>{move body: with<core.error.throwing<core.string.String>>() :()}: () = builtin()\n\n",
        ),
        (
            "requires",
            "pub let requires<\n  condition: bool,\n  e: effects,\n  Result: type,\n>: with<e>\n  {move body: with<e>() :Result}: Result = builtin()\n",
        ),
    ] {
        let missing = EDITION_2026_LIB.replace(declaration, "");
        assert_ne!(missing, EDITION_2026_LIB);
        let modules = edition_2026_test_modules(&[("lib", &missing)]);
        let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
        assert!(
            error
                .diagnostics()
                .iter()
                .any(|diagnostic| diagnostic == &format!("missing lang item `{name}`")),
            "{:?}",
            error.diagnostics()
        );
    }

    assert!(
        LangItemKind::ALL
            .iter()
            .all(|kind| kind.source_name() != "extend"),
        "`extend` is parser-owned declaration syntax, not a callable lang item"
    );

    for (module, name, malformed) in [
        (
            "foreign",
            "foreign",
            EDITION_2026_FOREIGN.replace(
                "pub let foreign<abi: abi>: never = builtin()",
                "pub let foreign(): never = builtin()",
            ),
        ),
        (
            "foreign",
            "foreign",
            EDITION_2026_FOREIGN.replace(
                "pub let foreign<abi: abi>: never = builtin()",
                "pub let foreign<abi: abi>: i32 = builtin()",
            ),
        ),
        (
            "foreign",
            "foreign",
            EDITION_2026_FOREIGN.replace(
                "pub let foreign<abi: abi, symbol: String>: never = builtin()",
                "pub let foreign<abi: abi, symbol: usize>: never = builtin()",
            ),
        ),
        (
            "lib",
            "test",
            EDITION_2026_LIB.replace(
                "move body: with<core.error.throwing<core.string.String>>() :()",
                "move body: ()",
            ),
        ),
        (
            "lib",
            "test",
            EDITION_2026_LIB.replace(
                "move body: with<core.error.throwing<core.string.String>>() :()",
                "move body: with<core.error.throwing<core.string.String>>() :i32",
            ),
        ),
        (
            "lib",
            "test",
            EDITION_2026_LIB.replace("<name: String>", "<name: usize>"),
        ),
        (
            "lib",
            "requires",
            EDITION_2026_LIB.replace("condition: bool,", "condition: usize,"),
        ),
        (
            "lib",
            "requires",
            EDITION_2026_LIB.replace("move body: with<e>() :Result", "move body: Result"),
        ),
    ] {
        assert_ne!(
            malformed,
            match module {
                "foreign" => EDITION_2026_FOREIGN,
                "lib" => EDITION_2026_LIB,
                _ => unreachable!(),
            }
        );
        let modules = edition_2026_test_modules(&[(module, &malformed)]);
        let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
        assert!(
            error
                .diagnostics()
                .iter()
                .any(|diagnostic| diagnostic.contains(&format!("syntax lang item `{name}`"))),
            "{:?}",
            error.diagnostics()
        );
    }

    let missing_primitive_marker =
        EDITION_2026_PRIMITIVES.replace("pub let i32: type = builtin()", "pub let i32: type");
    let modules = edition_2026_test_modules(&[("primitives", &missing_primitive_marker)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error.diagnostics().iter().any(|diagnostic| {
        diagnostic.contains("compiler-owned lang item `i32`") && diagnostic.contains("= builtin()")
    }));

    let unknown = format!("{EDITION_2026_PRIMITIVES}\npub let mystery(): i32 = builtin()\n");
    let modules = edition_2026_test_modules(&[("primitives", &unknown)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error.diagnostics().iter().any(|diagnostic| {
        diagnostic.contains("unknown compiler-owned core function `mystery`")
    }));

    let malformed_defer = EDITION_2026_CONTROL.replace(
        "with<e>{move action: with<e>() :()}: () = builtin()",
        "with<e>{move action: with<e>() :bool}: () = builtin()",
    );
    assert_ne!(malformed_defer, EDITION_2026_CONTROL);
    let modules = edition_2026_test_modules(&[("control", &malformed_defer)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error.diagnostics().iter().any(|diagnostic| {
        diagnostic.contains("compiler-owned support function `defer`")
            && diagnostic.contains("builtin()")
    }));

    let abstract_builtin = EDITION_2026_MARKER.replace(
        "drop(self: Borrow<mut><self>)\n  (): ()",
        "drop(self: Borrow<mut><self>)\n  (): () = builtin()",
    );
    assert_ne!(abstract_builtin, EDITION_2026_MARKER);
    let modules = edition_2026_test_modules(&[("marker", &abstract_builtin)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(
        error.diagnostics().iter().any(|diagnostic| {
            diagnostic.contains("trait default implementations cannot use `builtin()`")
                && diagnostic.contains("cannot use `builtin()`")
        }),
        "{:?}",
        error.diagnostics()
    );
}

#[test]
fn constraint_query_contracts_are_explicit_and_bounded() {
    for malformed in [
        EDITION_2026_SORTS.replace("pub let constraint: sort<2>", "pub let constraint: sort<1>"),
        EDITION_2026_SORTS.replace("right: constraint", "right: type"),
        EDITION_2026_SORTS.replace(">: bool = builtin()", ">: usize = builtin()"),
    ] {
        let modules = edition_2026_test_modules(&[("sorts", &malformed)]);
        let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
        assert!(
            error
                .diagnostics()
                .iter()
                .any(|diagnostic| diagnostic.contains("constraint")),
            "{:?}",
            error.diagnostics()
        );
    }
}

#[test]
fn derived_primitive_operations_are_source_defined() {
    let bundle = CoreBundle::for_edition(Edition::Edition2026).unwrap();
    let expected = BTreeMap::from([
        ("not", 1),
        ("eq", 4),
        ("neg", 6),
        ("add_assign", 12),
        ("sub_assign", 12),
        ("mul_assign", 12),
        ("div_assign", 12),
        ("rem_assign", 12),
        ("bit_and_assign", 12),
        ("bit_or_assign", 12),
        ("bit_xor_assign", 12),
        ("shl_assign", 12),
        ("shr_assign", 12),
    ]);
    let mut actual = BTreeMap::<&str, usize>::new();

    for item in &bundle.program().items {
        let Item::Extend(extension) = item else {
            continue;
        };
        for member in &extension.members {
            let crate::ast::ExtendMember::Function(function) = member else {
                continue;
            };
            let Some((name, _)) = expected.get_key_value(function.name.as_str()) else {
                continue;
            };
            if function.body.is_some() && !function.builtin {
                *actual.entry(name).or_default() += 1;
            }
        }
    }

    assert_eq!(actual, expected);
}

#[test]
fn await_is_source_defined_while_async_remains_intrinsic() {
    let bundle = CoreBundle::for_edition(Edition::Edition2026).unwrap();
    let async_function = &bundle.program().items[bundle.lang_items().async_function().item_index()];
    let await_function = &bundle.program().items[bundle.lang_items().await_function().item_index()];

    assert!(matches!(
        async_function,
        Item::Function(function) if function.builtin && function.body.is_none()
    ));
    assert!(matches!(
        await_function,
        Item::Function(function) if !function.builtin && function.body.is_some()
    ));
}

#[test]
fn bool_lang_item_requires_its_enum_variants() {
    let malformed = EDITION_2026_PRIMITIVES.replace(
        "pub let bool = enum { false, true }",
        "pub let bool = enum { true }",
    );
    let modules = edition_2026_test_modules(&[("primitives", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error.diagnostics().iter().any(|diagnostic| {
        diagnostic == "lang item `bool` must have shape `pub let bool = enum { false, true }`"
    }));
}

#[test]
fn pointer_and_layout_lang_items_require_memory_contracts() {
    let modules = edition_2026_test_modules(&[("memory", "")]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    for name in ["Array", "Slice", "Ptr", "ptr", "size_of", "align_of"] {
        assert!(
            error
                .diagnostics()
                .iter()
                .any(|diagnostic| diagnostic == &format!("missing lang item `{name}`")),
            "{:?}",
            error.diagnostics()
        );
    }

    for (name, malformed) in [
        (
            "Array",
            EDITION_2026_MEMORY.replace(
                "  <l: usize>: type = builtin()",
                "  <length: usize>: type = builtin()",
            ),
        ),
        (
            "Slice",
            EDITION_2026_MEMORY.replace(
                "pub let Slice<T: type>: type = builtin()",
                "pub let Slice<Element: type>: type = builtin()",
            ),
        ),
        (
            "ptr",
            EDITION_2026_MEMORY.replace(
                "(value: Borrow<a><T>): Ptr<a><T> = builtin()",
                "(value: Borrow<T>): Ptr<a><T> = builtin()",
            ),
        ),
        (
            "size_of",
            EDITION_2026_MEMORY.replace(
                "pub let size_of<T: type>: u64 = builtin()",
                "pub let size_of<T: type>: i32 = builtin()",
            ),
        ),
        (
            "align_of",
            EDITION_2026_MEMORY.replace(
                "pub let align_of<T: type>: u64 = builtin()",
                "pub let align_of<T: type>(value: T): u64 = builtin()",
            ),
        ),
    ] {
        let modules = edition_2026_test_modules(&[("memory", &malformed)]);
        let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
        assert!(
            error
                .diagnostics()
                .iter()
                .any(|diagnostic| diagnostic.contains(&format!("lang item `{name}`"))),
            "{:?}",
            error.diagnostics()
        );
    }
}

#[test]
fn borrow_lang_items_require_the_borrow_module() {
    let modules = edition_2026_test_modules(&[("borrow", "")]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .contains(&"missing lang item `Borrow`".to_owned()));
    assert!(error
        .diagnostics()
        .contains(&"missing lang item `borrow`".to_owned()));
}

#[test]
fn rejects_malformed_control_contracts() {
    for (name, malformed) in [
            (
                "loop_exit",
                EDITION_2026_CONTROL.replace(
                    "exit(move value: T): never",
                    "exit(value: T): never",
                ),
            ),
            (
                "continue",
                EDITION_2026_CONTROL.replace(
                    "pub let continue: with<iteration_skip>\n  (): never =",
                    "pub let continue: with<iteration_skip>\n  (): () =",
                ),
            ),
            (
                "return",
                EDITION_2026_CONTROL.replace(
                    "with<function_exit<T>>\n  (move value: T): never",
                    "with<function_exit<T>>\n  (value: T): never",
                ),
            ),
            (
                "do",
                EDITION_2026_CONTROL.replace(
                    "{move condition: with<core.control.loop_exit<()>, core.control.iteration_skip, e>() :bool}: ()",
                    "{move until: with<core.control.loop_exit<()>, core.control.iteration_skip, e>() :bool}: ()",
                ),
            ),
            (
                "if",
                EDITION_2026_CONTROL.replace(
                    "with<e>\n  (condition: bool)\n  {move then: with<e>(): T}",
                    "with<e>\n  (condition: i32)\n  {move then: with<e>(): T}",
                ),
            ),
            (
                "match",
                EDITION_2026_CONTROL.replace(
                    "  ...cases: Output",
                    "  (case: Input): Output",
                ),
            ),
            (
                "for",
                EDITION_2026_CONTROL.replace(
                    "  Iter.Item == Item",
                    "  Iter.Item == bool",
                ),
            ),
        ] {
            assert_ne!(malformed, EDITION_2026_CONTROL, "stale `{name}` mutation");
            let modules = edition_2026_test_modules(&[("control", &malformed)]);
            let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
            assert!(
                error
                    .diagnostics()
                    .iter()
                    .any(|diagnostic| diagnostic.contains(&format!("lang item `{name}`"))),
                "{:?}",
                error.diagnostics()
            );
        }

    let malformed = EDITION_2026_UNSAFE.replace(
        "pub let unsafe<e: effects, T: type>: with<e>\n  {move action: with<core.unsafe.unsafety, e>() :T}: T",
        "pub let unsafe<e: effects, T: type>: with<e>\n  {move action: with<e>() :T}: T",
    );
    let modules = edition_2026_test_modules(&[("unsafe", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `unsafe`")));

    let bodyless = EDITION_2026_UNSAFE.replace(
        ": T = {\n  core.unsafe.unsafety.handle {\n    action: { action() },\n  }\n}",
        ": T",
    );
    let modules = edition_2026_test_modules(&[("unsafe", &bodyless)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `unsafe`")));

    let malformed = EDITION_2026_EFFECT.replace(
        "pub let EffectCallable<Input: type, Output: type, Answer: type>: type = builtin()",
        "pub let EffectCallable<Input: type, Output: type>: type = builtin()",
    );
    let modules = edition_2026_test_modules(&[("effect", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `EffectCallable`")));

    for (source_declaration, malformed_declaration, name) in [
        (
            "pub let Continuation<Input: type, Output: type>: type = builtin()",
            "pub let Continuation<Input: type, Output: type> = struct {}",
            "Continuation",
        ),
        (
            "pub let EffectCallable<Input: type, Output: type, Answer: type>: type = builtin()",
            "pub let EffectCallable<Input: type, Output: type, Answer: type> = struct {}",
            "EffectCallable",
        ),
    ] {
        let malformed = EDITION_2026_EFFECT.replace(source_declaration, malformed_declaration);
        let modules = edition_2026_test_modules(&[("effect", &malformed)]);
        let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
        assert!(error.diagnostics().iter().any(|diagnostic| {
            diagnostic.contains(&format!(
                "lang item `{name}` must be type form, found struct"
            ))
        }));
    }

    let malformed = EDITION_2026_EFFECT.replace(
        "pub let Handle = trait<self: effect>",
        "pub let Handle = trait",
    );
    let modules = edition_2026_test_modules(&[("effect", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `Handle`")));

    let malformed = EDITION_2026_EFFECT
        .replace(
            "Arguments<Value: type, Answer: type>: parameters",
            "Arguments<Value: type, Answer: type>: type",
        )
        .replace(
            "...Arguments<Value, Answer>",
            "(move arguments: Arguments<Value, Answer>)",
        );
    let modules = edition_2026_test_modules(&[("effect", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `Handle`")));

    let malformed = EDITION_2026_ERROR.replace(
        "pub let throw<Error: type>: with<core.error.throwing<Error>>\n  (move error: Error): never",
        "pub let throw<Error: type>\n  (move error: Error): never",
    );
    assert_ne!(malformed, EDITION_2026_ERROR, "stale `throw` mutation");
    let modules = edition_2026_test_modules(&[("error", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `throw`")));
}

#[test]
fn privileged_runtime_group_delimiters_are_validated() {
    let cases = [
        ("control", "do", EDITION_2026_CONTROL.replace("{move action: with<e>() :T}", "(move action: with<e>(): T)")),
        ("control", "post-test do", EDITION_2026_CONTROL.replace("{move condition: with<core.control.loop_exit<()>, core.control.iteration_skip, e>() :bool}", "(move condition: with<core.control.loop_exit<()>, core.control.iteration_skip, e>(): bool)")),
        ("control", "defer", EDITION_2026_CONTROL.replace("{move action: with<e>() :()}: () = builtin()", "(move action: with<e>(): ()): () = builtin()")),
        ("error", "try", EDITION_2026_ERROR.replace("{move action: with<core.error.throwing<Error>, f>() :T}", "(move action: with<core.error.throwing<Error>, f>(): T)")),
        ("unsafe", "unsafe", EDITION_2026_UNSAFE.replace("{move action: with<core.unsafe.unsafety, e>() :T}", "(move action: with<core.unsafe.unsafety, e>(): T)")),
        ("control", "loop", EDITION_2026_CONTROL.replace("{move body: with<core.control.loop_exit<T>, core.control.iteration_skip, e>() :()}", "(move body: with<core.control.loop_exit<T>, core.control.iteration_skip, e>(): ())")),
        ("control", "while", EDITION_2026_CONTROL.replace("{move do: with<e>(): ()}", "(move do: with<e>(): ())")),
        ("control", "if", EDITION_2026_CONTROL.replace("{move then: with<e>(): T}", "(move then: with<e>(): T)")),
        ("control", "for", EDITION_2026_CONTROL.replace("{move body: with<core.control.loop_exit<()>, core.control.iteration_skip, e>(Item): ()}", "(move body: with<core.control.loop_exit<()>, core.control.iteration_skip, e>(Item): ())")),
        ("lib", "test", EDITION_2026_LIB.replace("{move body: with<core.error.throwing<core.string.String>>() :()}", "(move body: with<core.error.throwing<core.string.String>>(): ())")),
        ("lib", "requires", EDITION_2026_LIB.replace("{move body: with<e>() :Result}", "(move body: with<e>(): Result)")),
        ("async", "async", EDITION_2026_ASYNC.replace("{move action: with<core.async.suspension, e>() :T}", "(move action: with<core.async.suspension, e>(): T)")),
        ("effect", "Handle", EDITION_2026_EFFECT.replace("...Arguments<Value, Answer>", "(move arguments: i32)")),
    ];

    for (module, name, malformed) in cases {
        let modules = edition_2026_test_modules(&[(module, &malformed)]);
        let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
        assert!(
            error
                .diagnostics()
                .iter()
                .any(|diagnostic| diagnostic.contains(name.split_whitespace().last().unwrap())),
            "{name}: {:?}",
            error.diagnostics()
        );
    }
}

#[test]
fn rejects_malformed_async_contracts() {
    for (name, malformed) in [
        (
            "suspension",
            EDITION_2026_ASYNC.replace("suspend(): ()", "suspend(): i32"),
        ),
        ("Poll", EDITION_2026_ASYNC.replace("  Pending,\n", "")),
        (
            "Future",
            EDITION_2026_ASYNC.replace(
                "trait<requires: self is Movable>",
                "trait<requires: self is Copyable>",
            ),
        ),
        (
            "Executor",
            EDITION_2026_ASYNC
                .replace("run<e: effects, F: type, T: type>", "run<F: type, T: type>"),
        ),
        (
            "async",
            EDITION_2026_ASYNC.replace(
                "{move action: with<core.async.suspension, e>() :T}: F",
                "{move action: with<e>() :T}: F",
            ),
        ),
        (
            "await",
            EDITION_2026_ASYNC.replace(
                "with<core.async.suspension, e>\n  (move future: F): T",
                "with<e>\n  (move future: F): T",
            ),
        ),
    ] {
        let modules = edition_2026_test_modules(&[("async", &malformed)]);
        let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
        assert!(
            error
                .diagnostics()
                .iter()
                .any(|diagnostic| diagnostic.contains(&format!("lang item `{name}`"))),
            "{:?}",
            error.diagnostics()
        );
    }
}

#[test]
fn rejects_malformed_iteration_contracts() {
    let malformed = EDITION_2026_ITER.replace(
        "next<r: region>(self: Borrow<mut><r><self>)\n  (): core.Option<Item<r>>",
        "next<r: region>(self: Borrow<r><self>)\n  (): core.Option<Item<r>>",
    );
    let modules = edition_2026_test_modules(&[("iter", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `Iterator`")));
}

#[test]
fn rejects_malformed_assignment_operator_contracts() {
    let malformed = EDITION_2026_OPS_ASSIGN.replace(
        "add_assign(self: Borrow<mut><self>)\n  (rhs: Rhs): ()",
        "add_assign(self: Borrow<self>)\n  (rhs: Rhs): ()",
    );
    let modules = edition_2026_test_modules(&[("ops/assign", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `AddAssign`")));
}

#[test]
fn rejects_malformed_index_contracts() {
    for malformed in [
            "pub let Index = trait {}",
            "pub let Index<Key: type> = trait { Output: type; index(self)(key: Key): Output }",
            "pub let Index<Key: type> = trait { Output: type; index<a: access>(self: Borrow<self>)(key: Key): Borrow<a><Output> }",
        ] {
            let modules = edition_2026_test_modules(&[("ops/index", malformed)]);
            let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
            assert!(
                error
                    .diagnostics()
                    .iter()
                    .any(|diagnostic| diagnostic.contains("lang item `Index` must have shape")),
                "{malformed}: {:?}",
                error.diagnostics()
            );
        }
}

#[test]
fn rejects_malformed_flow_operator_contracts() {
    let malformed = EDITION_2026_FLOW.replace("Rebind<Value: type>: type", "Rebind: type");
    let modules = edition_2026_test_modules(&[("flow", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `Chain`")));

    let malformed = EDITION_2026_FLOW.replace(
        "coalesce<e: effects>:with<e>(self)(fallback: with<e>() :Item): Item",
        "coalesce(move self)\n    (move fallback: (): Item): Item",
    );
    let modules = edition_2026_test_modules(&[("flow", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `Coalesce`")));

    let malformed = EDITION_2026_FLOW.replace("unwrap(move self): Output", "unwrap(self): Output");
    let modules = edition_2026_test_modules(&[("flow", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `Unwrap`")));

    let malformed = EDITION_2026_FLOW.replace(
        "raise: with<core.error.throwing<Error>>(move self): Output",
        "raise(move self): Output",
    );
    let modules = edition_2026_test_modules(&[("flow", &malformed)]);
    let error = CoreBundle::from_modules(Edition::Edition2026, &modules).unwrap_err();
    assert!(error
        .diagnostics()
        .iter()
        .any(|diagnostic| diagnostic.contains("lang item `Raise`")));
}

#[test]
fn lang_item_identities_follow_validated_declarations_not_source_order() {
    let source = r#"
pub let Rem<Rhs: type> = trait {
  Output: type
  rem(self)(rhs: Rhs): Output
}
pub let Movable = trait {}
pub let Copyable = trait<requires: self is Movable> {}
pub let Droppable = trait {
  drop(self: Borrow<mut><self>)(): ()
}
pub let Add<Rhs: type> = trait {
  Output: type
  add(self)(rhs: Rhs): Output
}
pub let never = enum {}
pub let Option<T: type> = enum { Some(T), None }
pub let Result<Error: type><T: type> = enum { Ok(T), Err(Error) }
pub let Div<Rhs: type> = trait {
  Output: type
  div(self)(rhs: Rhs): Output
}
pub let Sub<Rhs: type> = trait {
  Output: type
  sub(self)(rhs: Rhs): Output
}
pub let Mul<Rhs: type> = trait {
  Output: type
  mul(self)(rhs: Rhs): Output
}
pub let Eq<Rhs: type> = trait {
  eq(self: Borrow<self>)(rhs: Borrow<Rhs>): bool
}
pub let PartialOrdering = enum { Less, Equal, Greater, Unordered }
pub let PartialOrd<Rhs: type> = trait {
  partial_cmp(self: Borrow<self>)(rhs: Borrow<Rhs>): PartialOrdering
}
pub let Neg = trait {
  Output: type
  neg(self)(): Output
}
pub let Not = trait {
  Output: type
  not(self)(): Output
}
pub let BitAnd<Rhs: type> = trait {
  Output: type
  bit_and(self)(rhs: Rhs): Output
}
pub let BitOr<Rhs: type> = trait {
  Output: type
  bit_or(self)(rhs: Rhs): Output
}
pub let BitXor<Rhs: type> = trait {
  Output: type
  bit_xor(self)(rhs: Rhs): Output
}
pub let Shl<Rhs: type> = trait {
  Output: type
  shl(self)(rhs: Rhs): Output
}
pub let Shr<Rhs: type> = trait {
  Output: type
  shr(self)(rhs: Rhs): Output
}
pub let Index<Key: type> = trait {
  Output: type
  index<a: access>(self: Borrow<a><self>)(key: Key): Borrow<a><Output>
}
pub let str: type = builtin()
"#;
    let bundle = core_bundle_from_source(source).unwrap();

    assert_eq!(bundle.lang_items().rem().item_index(), 0);
    assert_eq!(bundle.lang_items().move_trait().item_index(), 1);
    assert_eq!(bundle.lang_items().copy().item_index(), 2);
    assert_eq!(bundle.lang_items().drop().item_index(), 3);
    assert_eq!(bundle.lang_items().add().item_index(), 4);
    assert_eq!(bundle.lang_items().never().item_index(), 5);
    assert_eq!(bundle.lang_items().option().item_index(), 6);
    assert_eq!(bundle.lang_items().result().item_index(), 7);
    assert_eq!(bundle.lang_items().div().item_index(), 8);
    assert_eq!(bundle.lang_items().sub().item_index(), 9);
    assert_eq!(bundle.lang_items().mul().item_index(), 10);
    assert_eq!(bundle.lang_items().eq().item_index(), 11);
    assert_eq!(bundle.lang_items().partial_ordering().item_index(), 12);
    assert_eq!(bundle.lang_items().partial_ord().item_index(), 13);
    assert_eq!(bundle.lang_items().neg().item_index(), 14);
    assert_eq!(bundle.lang_items().not().item_index(), 15);
    assert_eq!(bundle.lang_items().bit_and().item_index(), 16);
    assert_eq!(bundle.lang_items().bit_or().item_index(), 17);
    assert_eq!(bundle.lang_items().bit_xor().item_index(), 18);
    assert_eq!(bundle.lang_items().shl().item_index(), 19);
    assert_eq!(bundle.lang_items().shr().item_index(), 20);
    assert_eq!(bundle.lang_items().index().item_index(), 21);
    assert_eq!(bundle.lang_items().str_type_form().item_index(), 22);
    for kind in LangItemKind::ALL {
        let item = bundle.lang_items().get(kind);
        assert_eq!(
            item.canonical_name(),
            item_name(&bundle.program().items[item.item_index()]).unwrap()
        );
    }
}

#[test]
fn rejects_wrong_visibility_kind_shape_and_extra_items_deterministically() {
    let source = r#"
let Option<T: type> = enum { Some(T), None }
pub let Result = struct { value: i32 }
pub let never = enum { Reachable }
pub let Movable = trait {}
pub let Copyable<T: type> = trait {}
pub let Add<Rhs: type> = trait {
  add(self)(rhs: Rhs): rhs
}
pub let Extra = enum {}
pub let Sub<Rhs: type> = trait {
  Output: type
  sub(self)(rhs: Rhs): Output
}
pub let Mul<Rhs: type> = trait {
  Output: type
  mul(self)(rhs: Rhs): Output
}
pub let Div<Rhs: type> = trait {
  Output: type
  div(self)(rhs: Rhs): Output
}
pub let Rem<Rhs: type> = trait {
  Output: type
  rem(self)(rhs: Rhs): Output
}
pub let Eq<Rhs: type> = trait {
  eq(self: Borrow<self>)(rhs: Borrow<Rhs>): bool
}
pub let PartialOrdering = enum { Less, Equal, Greater, Unordered }
pub let PartialOrd<Rhs: type> = trait {
  partial_cmp(self: Borrow<self>)(rhs: Borrow<Rhs>): PartialOrdering
}
pub let Neg = trait {
  Output: type
  neg(self)(): Output
}
pub let Not = trait {
  Output: type
  not(self)(): Output
}
pub let BitAnd<Rhs: type> = trait {
  Output: type
  bit_and(self)(rhs: Rhs): Output
}
pub let BitOr<Rhs: type> = trait {
  Output: type
  bit_or(self)(rhs: Rhs): Output
}
pub let BitXor<Rhs: type> = trait {
  Output: type
  bit_xor(self)(rhs: Rhs): Output
}
pub let Shl<Rhs: type> = trait {
  Output: type
  shl(self)(rhs: Rhs): Output
}
pub let Shr<Rhs: type> = trait {
  Output: type
  shr(self)(rhs: Rhs): Output
}
pub let Droppable = trait {
  drop(self: Borrow<mut><self>)(): ()
}
pub let str: type = builtin()
"#;
    let error = core_bundle_from_source(source).unwrap_err();

    assert_eq!(
            error.diagnostics(),
            [
                "lang item `Option` must be `pub`, found private visibility",
                "unexpected declaration `Extra` at item 7",
                "lang item `Result` must be enum, found struct",
                "lang item `never` must have shape `pub let never = enum {}`",
                "lang item `Copyable` must have shape `pub let Copyable = trait<requires: self is Movable> {}`",
                "lang item `Add` must have shape `pub let Add<Rhs: type> = trait { Output: type; add(self)(rhs: Rhs): Output }`",
                "missing lang item `Index`",
            ]
        );
    assert_eq!(
            error.to_string(),
             "invalid embedded core bundle for edition 2026\n- lang item `Option` must be `pub`, found private visibility\n- unexpected declaration `Extra` at item 7\n- lang item `Result` must be enum, found struct\n- lang item `never` must have shape `pub let never = enum {}`\n- lang item `Copyable` must have shape `pub let Copyable = trait<requires: self is Movable> {}`\n- lang item `Add` must have shape `pub let Add<Rhs: type> = trait { Output: type; add(self)(rhs: Rhs): Output }`\n- missing lang item `Index`"
        );
}

#[test]
fn rejects_missing_and_duplicate_lang_items_in_fixed_role_order() {
    let source = r#"
pub let Option<T: type> = enum { Some(T), None }
pub let Option<T: type> = enum { Some(T), None }
pub let never = enum {}
pub let Add<Rhs: type> = trait {
  Output: type
  add(self)(rhs: Rhs): Output
}
pub let Sub<Rhs: type> = trait {
  Output: type
  sub(self)(rhs: Rhs): Output
}
pub let Mul<Rhs: type> = trait {
  Output: type
  mul(self)(rhs: Rhs): Output
}
pub let Div<Rhs: type> = trait {
  Output: type
  div(self)(rhs: Rhs): Output
}
pub let Rem<Rhs: type> = trait {
  Output: type
  rem(self)(rhs: Rhs): Output
}
pub let Eq<Rhs: type> = trait {
  eq(self: Borrow<self>)(rhs: Borrow<Rhs>): bool
}
pub let PartialOrdering = enum { Less, Equal, Greater, Unordered }
pub let PartialOrd<Rhs: type> = trait {
  partial_cmp(self: Borrow<self>)(rhs: Borrow<Rhs>): PartialOrdering
}
pub let Neg = trait {
  Output: type
  neg(self)(): Output
}
pub let Not = trait {
  Output: type
  not(self)(): Output
}
pub let BitAnd<Rhs: type> = trait {
  Output: type
  bit_and(self)(rhs: Rhs): Output
}
pub let BitOr<Rhs: type> = trait {
  Output: type
  bit_or(self)(rhs: Rhs): Output
}
pub let BitXor<Rhs: type> = trait {
  Output: type
  bit_xor(self)(rhs: Rhs): Output
}
pub let Shl<Rhs: type> = trait {
  Output: type
  shl(self)(rhs: Rhs): Output
}
pub let Shr<Rhs: type> = trait {
  Output: type
  shr(self)(rhs: Rhs): Output
}
pub let str: type = builtin()
"#;
    let error = core_bundle_from_source(source).unwrap_err();

    assert_eq!(
        error.diagnostics(),
        [
            "duplicate lang item `Option` appears 2 times",
            "missing lang item `Result`",
            "missing lang item `Movable`",
            "missing lang item `Copyable`",
            "missing lang item `Droppable`",
            "missing lang item `Index`",
        ]
    );
}

#[test]
fn rejects_copy_compile_parameters_associated_types_and_methods() {
    let malformed_declarations = [
        "pub let Copyable<T: type> = trait {}",
        "pub let Copyable = trait { Item: type }",
        "pub let Copyable = trait { clone(self: Borrow<self>)(): self }",
    ];

    for declaration in malformed_declarations {
        let source = core_source_with_copy(declaration);
        let error = core_bundle_from_source(&source).unwrap_err();

        assert_eq!(
                error.diagnostics(),
                ["lang item `Copyable` must have shape `pub let Copyable = trait<requires: self is Movable> {}`"],
                "unexpected diagnostic for `{declaration}`"
            );
    }
}

#[test]
fn rejects_malformed_move_traits_and_copy_without_move_supertrait() {
    for malformed in [
        "pub let Movable<T: type> = trait {}",
        "pub let Movable = trait { Item: type }",
        "pub let Movable = trait<requires: self is Copyable> {}",
    ] {
        let source =
            core_source_with_copy("pub let Copyable = trait<requires: self is Movable> {}")
                .replacen("pub let Movable = trait {}", malformed, 1);
        let error = core_bundle_from_source(&source).unwrap_err();
        assert_eq!(
            error.diagnostics(),
            ["lang item `Movable` must have shape `pub let Movable = trait {}`"],
            "unexpected diagnostic for `{malformed}`"
        );
    }

    let source = core_source_with_copy("pub let Copyable = trait {}");
    let error = core_bundle_from_source(&source).unwrap_err();
    assert_eq!(
        error.diagnostics(),
        ["lang item `Copyable` must have shape `pub let Copyable = trait<requires: self is Movable> {}`"]
    );
}

#[test]
fn rejects_malformed_drop_traits() {
    let malformed_declarations = [
        "pub let Droppable<T: type> = trait { drop(self: Borrow<mut><self>)(): () }",
        "pub let Droppable = trait {}",
        "pub let Droppable = trait { drop(self: Borrow<self>)(): () }",
        "pub let Droppable = trait { drop(self: Borrow<mut><self>)(): i32 }",
    ];

    for declaration in malformed_declarations {
        let source =
            core_source_with_copy("pub let Copyable = trait<requires: self is Movable> {}")
                .replacen(
                    "pub let Droppable = trait {\n  drop(self: Borrow<mut><self>)(): ()\n}",
                    declaration,
                    1,
                );
        let error = core_bundle_from_source(&source).unwrap_err();
        assert_eq!(
                error.diagnostics(),
                ["lang item `Droppable` must have shape `pub let Droppable = trait { drop(self: Borrow<mut><self>)(): () }`"],
                "unexpected diagnostic for `{declaration}`"
            );
    }
}

#[test]
fn rejects_malformed_operator_traits_in_fixed_role_order() {
    let source = r#"
pub let Option<T: type> = enum { Some(T), None }
pub let Result<Error: type><T: type> = enum { Ok(T), Err(Error) }
pub let never = enum {}
pub let Movable = trait {}
pub let Copyable = trait<requires: self is Movable> {}
pub let Droppable = trait {
  drop(self: Borrow<mut><self>)(): ()
}
pub let Add<Rhs: type> = trait {
  Output: type
  add(self)(rhs: Rhs): Output
}
pub let Sub = trait {
  Output: type
  sub(self)(rhs: Rhs): Output
}
pub let Mul<Rhs: type> = trait {
  mul(self)(rhs: Rhs): rhs
}
pub let Div<Rhs: type> = trait {
  Output: type
  Divide(self)(rhs: Rhs): Output
}
pub let Rem<Rhs: type> = trait {
  Output: type
  rem(self)(rhs: Rhs): Output = rhs
}
pub let Eq<Rhs: type> = trait {
  eq(move self)(rhs: Rhs): bool
}
pub let PartialOrdering = enum { Less, Equal, Greater, Unordered }
pub let PartialOrd<Rhs: type> = trait {
  partial_cmp(move self)(rhs: Rhs): PartialOrdering
}
pub let Neg = trait {
  Output: type
  neg(self)(): Output
}
pub let Not = trait {
  Output: type
  not(self)(): Output
}
pub let BitAnd<Rhs: type> = trait {
  Output: type
  bit_and(self)(rhs: Rhs): Output
}
pub let BitOr<Rhs: type> = trait {
  Output: type
  bit_or(self)(rhs: Rhs): Output
}
pub let BitXor<Rhs: type> = trait {
  Output: type
  bit_xor(self)(rhs: Rhs): Output
}
pub let Shl<Rhs: type> = trait {
  Output: type
  shl(self)(rhs: Rhs): Output
}
pub let Shr<Rhs: type> = trait {
  Output: type
  shr(self)(rhs: Rhs): Output
}
pub let Index<Key: type> = trait {
  Output: type
  index<a: access>(self: Borrow<a><self>)(key: Key): Borrow<a><Output>
}
pub let str: type = builtin()
"#;
    let error = core_bundle_from_source(source).unwrap_err();

    assert_eq!(
            error.diagnostics(),
            [
                "public core function `Divide` violates standard naming: use ASCII `snake_case` without leading, trailing, or repeated underscores",
                "lang item `Sub` must have shape `pub let Sub<Rhs: type> = trait { Output: type; sub(self)(rhs: Rhs): Output }`",
                "lang item `Mul` must have shape `pub let Mul<Rhs: type> = trait { Output: type; mul(self)(rhs: Rhs): Output }`",
                "lang item `Div` must have shape `pub let Div<Rhs: type> = trait { Output: type; div(self)(rhs: Rhs): Output }`",
                "lang item `Rem` must have shape `pub let Rem<Rhs: type> = trait { Output: type; rem(self)(rhs: Rhs): Output }`",
                "lang item `Eq` must have shape `pub let Eq<Rhs: type> = trait { eq(self: Borrow<self>)(rhs: Borrow<Rhs>): bool }`",
                "lang item `PartialOrd` must have shape `pub let PartialOrd<Rhs: type> = trait { partial_cmp(self: Borrow<self>)(rhs: Borrow<Rhs>): PartialOrdering }`",
            ]
        );
}

#[test]
fn rejects_malformed_partial_ordering() {
    for declaration in [
        "pub let PartialOrdering<T: type> = enum { Less, Equal, Greater, Unordered }",
        "pub let PartialOrdering = enum { Less, Equal, Greater }",
        "pub let PartialOrdering = enum { Less, Equal, Greater, Unknown }",
    ] {
        let source =
            core_source_with_copy("pub let Copyable = trait<requires: self is Movable> {}")
                .replacen(
                    "pub let PartialOrdering = enum { Less, Equal, Greater, Unordered }",
                    declaration,
                    1,
                );
        let error = core_bundle_from_source(&source).unwrap_err();
        assert_eq!(
                error.diagnostics(),
                ["lang item `PartialOrdering` must have shape `pub let PartialOrdering = enum { Less, Equal, Greater, Unordered }`"],
                "unexpected diagnostic for `{declaration}`"
            );
    }
}

#[test]
fn rejects_malformed_unary_operator_traits() {
    for (original, malformed, expected) in [
            (
                 "pub let Neg = trait {\n  Output: type\n  neg(self)(): Output\n}",
                 "pub let Neg<Rhs: type> = trait { neg(self)(): i32 }",
                 "lang item `Neg` must have shape `pub let Neg = trait { Output: type; neg(self)(): Output }`",
            ),
            (
                 "pub let Not = trait {\n  Output: type\n  not(self)(): Output\n}",
                 "pub let Not = trait { Output: type; not(self: Borrow<self>)(): Output }",
                 "lang item `Not` must have shape `pub let Not = trait { Output: type; not(self)(): Output }`",
            ),
        ] {
            let source =
                core_source_with_copy("pub let Copyable = trait<requires: self is Movable> {}").replacen(
                original,
                malformed,
                1,
            );
            let error = core_bundle_from_source(&source).unwrap_err();
            assert_eq!(error.diagnostics(), [expected]);
        }
}

#[test]
fn rejects_malformed_bitwise_operator_traits() {
    for (original, malformed, expected) in [
            (
                 "pub let BitAnd<Rhs: type> = trait {\n  Output: type\n  bit_and(self)(rhs: Rhs): Output\n}",
                 "pub let BitAnd = trait { bit_and(self: Borrow<self>)(move rhs: i32): i32 }",
                  "lang item `BitAnd` must have shape `pub let BitAnd<Rhs: type> = trait { Output: type; bit_and(self)(rhs: Rhs): Output }`",
            ),
            (
                 "pub let Shr<Rhs: type> = trait {\n  Output: type\n  shr(self)(rhs: Rhs): Output\n}",
                 "pub let Shr<Rhs: type> = trait { Output: type; shift(move self)(rhs: Rhs): Output }",
                  "lang item `Shr` must have shape `pub let Shr<Rhs: type> = trait { Output: type; shr(self)(rhs: Rhs): Output }`",
            ),
        ] {
            let source =
                core_source_with_copy("pub let Copyable = trait<requires: self is Movable> {}").replacen(
                original,
                malformed,
                1,
            );
            let error = core_bundle_from_source(&source).unwrap_err();
            assert_eq!(error.diagnostics(), [expected]);
        }
}

#[test]
fn reports_embedded_source_parse_errors() {
    let error =
        CoreBundle::from_source(Edition::Edition2026, "pub let option = enum {").unwrap_err();

    assert_eq!(error.diagnostics().len(), 1);
    assert!(error.diagnostics()[0].starts_with("embedded prelude does not parse: "));
}

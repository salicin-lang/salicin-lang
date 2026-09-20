//! Edition-pinned Salicin `std` sources.
//!
//! Unlike `core`, this bundle owns no language items. It contains only
//! higher-level, host-facing, or policy-bearing standard abstractions; lower
//! layers remain available through their canonical `core` and `alloc` paths.

use std::error::Error;
use std::fmt;
use std::sync::OnceLock;

use crate::ast::{ExtendMember, GroupDelimiter, Item, Program, TraitMember, Type, Visibility};
use crate::manifest::Edition;
use crate::modules::{self, PackageId, SourceUnit};
use crate::parser;

const EDITION_2026_LIB: &str = include_str!("../../library/std/src/lib.sc");
const EDITION_2026_ASYNC: &str = include_str!("../../library/std/src/async.sc");
const EDITION_2026_ALGEBRA: &str = include_str!("../../library/std/src/algebra.sc");
const EDITION_2026_FUNCTIONAL: &str = include_str!("../../library/std/src/functional.sc");
const EDITION_2026_IO: &str = include_str!("../../library/std/src/io.sc");
const EDITION_2026_TEST: &str = include_str!("../../library/std/src/test.sc");

const EDITION_2026_MODULES: &[(&str, &str)] = &[
    ("lib", EDITION_2026_LIB),
    ("async", EDITION_2026_ASYNC),
    ("algebra", EDITION_2026_ALGEBRA),
    ("functional", EDITION_2026_FUNCTIONAL),
    ("io", EDITION_2026_IO),
    ("test", EDITION_2026_TEST),
];

static EDITION_2026_BUNDLE: OnceLock<Result<StdBundle, StdBundleError>> = OnceLock::new();

pub(crate) fn incremental_sources(
    edition: Edition,
) -> impl Iterator<Item = (&'static str, &'static str)> {
    match edition {
        Edition::Edition2026 => EDITION_2026_MODULES.iter().copied(),
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct StdExport {
    pub(crate) module: String,
    pub(crate) name: String,
    pub(crate) target: Vec<String>,
}

const CATEGORY_SUFFIXES: &[&str] = &[
    "_effect",
    "_trait",
    "_type",
    "_struct",
    "_enum",
    "_function",
    "_sort",
];

pub(crate) fn naming_diagnostics(program: &Program, layer: &str) -> Vec<String> {
    let mut diagnostics = Vec::new();
    let mut check = |name: &str, category: &str, style: StandardNameStyle| {
        if let Some(reason) = validate_standard_name(name, category, style) {
            diagnostics.push(format!(
                "public {layer} {category} `{name}` violates standard naming: {reason}"
            ));
        }
    };
    for (item, visibility) in program.items.iter().zip(&program.item_visibilities) {
        if *visibility != Visibility::Public {
            continue;
        }
        match item {
            Item::Function(definition) => {
                check_function(definition, &mut check);
            }
            Item::Global(definition) => {
                check(&definition.name, "value", StandardNameStyle::SnakeCase)
            }
            Item::Struct(definition) => {
                check(
                    &definition.name,
                    "struct",
                    StandardNameStyle::PascalCase,
                );
                check_compile_parameters(&definition.compile_groups, &mut check);
            }
            Item::Enum(definition) => {
                let style = if matches!(definition.name.as_str(), "bool" | "never") {
                    StandardNameStyle::Primitive
                } else {
                    StandardNameStyle::PascalCase
                };
                check(&definition.name, "enum", style);
                check_compile_parameters(&definition.compile_groups, &mut check);
                for variant in &definition.variants {
                    let style = if definition.name == "bool"
                        && matches!(variant.name.as_str(), "false" | "true")
                    {
                        StandardNameStyle::Primitive
                    } else {
                        StandardNameStyle::PascalCase
                    };
                    check(&variant.name, "enum variant", style);
                }
            }
            Item::Effect(definition) => {
                check(&definition.name, "effect", StandardNameStyle::SnakeCase);
                check_compile_parameters(&definition.compile_groups, &mut check);
                for operation in &definition.operations {
                    check_function(operation, &mut check);
                }
            }
            Item::Sort(definition) => {
                check(&definition.name, "sort", StandardNameStyle::SnakeCase)
            }
            Item::TypeForm(definition) => {
                let style = if matches!(
                    definition.name.as_str(),
                    "str"
                        | "i8"
                        | "i16"
                        | "i32"
                        | "i64"
                        | "i128"
                        | "isize"
                        | "u8"
                        | "u16"
                        | "u32"
                        | "u64"
                        | "u128"
                        | "usize"
                ) {
                    StandardNameStyle::Primitive
                } else {
                    StandardNameStyle::PascalCase
                };
                check(&definition.name, "type", style);
                check_compile_parameters(&definition.compile_groups, &mut check);
            }
            Item::TypeAlias(definition) => {
                check(
                    &definition.name,
                    "type alias",
                    StandardNameStyle::PascalCase,
                );
                check_compile_parameters(&definition.compile_groups, &mut check);
            }
            Item::Trait(definition) => {
                check(
                    &definition.name,
                    "trait",
                    StandardNameStyle::PascalCase,
                );
                check_compile_parameters(&definition.compile_groups, &mut check);
                for member in &definition.members {
                    match member {
                        TraitMember::AssociatedType {
                            name,
                            compile_groups,
                            ..
                        } => {
                            check(name, "associated type", StandardNameStyle::PascalCase);
                            check_compile_parameters(compile_groups, &mut check);
                        }
                        TraitMember::Function(function)
                            if function.groups.is_empty()
                                && function.return_type
                                    == Some(Type::Named("parameters".to_owned(), Vec::new())) =>
                        {
                            check(
                                &function.name,
                                "associated parameter schema",
                                StandardNameStyle::PascalCase,
                            );
                            check_compile_parameters(&function.compile_groups, &mut check);
                        }
                        TraitMember::Function(function) => check_function(function, &mut check),
                    }
                }
            }
            Item::Extend(definition) => {
                check_compile_parameters(&definition.compile_groups, &mut check);
                for member in &definition.members {
                    match member {
                        ExtendMember::Const(definition) => check(
                                &definition.name,
                                "associated type",
                                StandardNameStyle::PascalCase,
                        ),
                        ExtendMember::Function(function) => {
                            check_function(function, &mut check)
                        }
                    }
                }
            }
        }
    }
    diagnostics
}

fn check_function(
    function: &crate::ast::Function,
    check: &mut impl FnMut(&str, &str, StandardNameStyle),
) {
    check(
        &function.name,
        "function",
        StandardNameStyle::SnakeCase,
    );
    check_compile_parameters(&function.compile_groups, check);
}

fn check_compile_parameters(
    groups: &[Vec<crate::ast::CompileParam>],
    check: &mut impl FnMut(&str, &str, StandardNameStyle),
) {
    for parameter in groups.iter().flatten() {
        let style = if parameter.kind == crate::ast::Sort::Type {
            StandardNameStyle::PascalCase
        } else {
            StandardNameStyle::SnakeCase
        };
        check(&parameter.name, "compile-time parameter", style);
    }
}

pub(crate) fn delimiter_diagnostics(program: &Program, layer: &str) -> Vec<String> {
    let mut diagnostics = Vec::new();
    let mut check = |function: &crate::ast::Function| {
        if function.effects.compile_group_delimiters.len() != function.compile_groups.len()
            || function
                .effects
                .compile_group_delimiters
                .iter()
                .any(|delimiter| *delimiter != GroupDelimiter::Angle)
        {
            diagnostics.push(format!(
                "official {layer} function `{}` must use `<...>` for every compile-time parameter group",
                function.name
            ));
        }
    };
    for item in &program.items {
        match item {
            Item::Function(function) => check(function),
            Item::Effect(effect) => effect.operations.iter().for_each(&mut check),
            Item::Trait(definition) => definition.members.iter().for_each(|member| {
                if let TraitMember::Function(function) = member {
                    check(function);
                }
            }),
            Item::Extend(definition) => definition.members.iter().for_each(|member| {
                if let ExtendMember::Function(function) = member {
                    check(function);
                }
            }),
            _ => {}
        }
    }
    diagnostics
}

#[derive(Clone, Copy)]
enum StandardNameStyle {
    PascalCase,
    SnakeCase,
    Primitive,
}

fn validate_standard_name(
    name: &str,
    category: &str,
    style: StandardNameStyle,
) -> Option<String> {
    if matches!(style, StandardNameStyle::Primitive) {
        return None;
    }
    if matches!(style, StandardNameStyle::PascalCase) {
        let mut bytes = name.bytes();
        let pascal_case = bytes.next().is_some_and(|byte| byte.is_ascii_uppercase())
            && bytes.all(|byte| byte.is_ascii_alphanumeric());
        return (!pascal_case).then(|| {
            "use ASCII `PascalCase` beginning with an uppercase letter and without underscores"
                .into()
        });
    }
    let ascii_snake_case = !name.is_empty()
        && !name.starts_with('_')
        && !name.ends_with('_')
        && !name.contains("__")
        && name
            .bytes()
            .all(|byte| byte.is_ascii_lowercase() || byte.is_ascii_digit() || byte == b'_');
    if !ascii_snake_case {
        return Some(
            "use ASCII `snake_case` without leading, trailing, or repeated underscores".into(),
        );
    }
    if let Some(suffix) = CATEGORY_SUFFIXES
        .iter()
        .find(|suffix| name.ends_with(**suffix))
    {
        return Some(format!(
            "name the {category} for its semantics instead of using the `{suffix}` category suffix"
        ));
    }
    None
}

#[derive(Clone, Debug, PartialEq)]
pub struct StdBundle {
    program: Program,
    exports: Vec<StdExport>,
}

impl StdBundle {
    pub fn for_edition(edition: Edition) -> Result<Self, StdBundleError> {
        Self::cached_for_edition(edition).cloned()
    }

    pub(crate) fn cached_for_edition(edition: Edition) -> Result<&'static Self, StdBundleError> {
        match EDITION_2026_BUNDLE.get_or_init(|| Self::load(edition)) {
            Ok(bundle) => Ok(bundle),
            Err(error) => Err(error.clone()),
        }
    }

    fn load(edition: Edition) -> Result<Self, StdBundleError> {
        validate_host_target(std::env::consts::OS, std::env::consts::ARCH, edition)?;
        Self::from_modules(edition, EDITION_2026_MODULES)
    }

    fn from_modules(edition: Edition, modules: &[(&str, &str)]) -> Result<Self, StdBundleError> {
        let mut exports = Vec::new();
        for &(module, source) in modules {
            let parsed = parser::parse(source).map_err(|error| {
                StdBundleError::new(
                    edition,
                    vec![format!(
                        "embedded std module `{module}` does not parse: {error}"
                    )],
                )
            })?;
            let naming = naming_diagnostics(&parsed, "std");
            let mut conventions = delimiter_diagnostics(&parsed, "std");
            conventions.extend(naming);
            if !conventions.is_empty() {
                return Err(StdBundleError::new(edition, conventions));
            }
            for (item, visibility) in parsed.items.iter().zip(&parsed.item_visibilities) {
                if *visibility != Visibility::Public {
                    continue;
                }
                let Some(name) = standard_item_name(item) else {
                    continue;
                };
                let mut target = vec!["std".to_owned()];
                if module != "lib" {
                    target.extend(module.split('/').map(str::to_owned));
                }
                target.push(name.to_owned());
                exports.push(StdExport {
                    module: if module == "lib" {
                        String::new()
                    } else {
                        module.replace('/', ".")
                    },
                    name: name.to_owned(),
                    target,
                });
            }
            if let Some(import) = parsed.uses.into_iter().next() {
                let Some(name) = import.alias else {
                    return Err(StdBundleError::new(
                        edition,
                        vec![format!(
                            "embedded std module `{module}` alias must have an explicit name"
                        )],
                    ));
                };
                if !matches!(
                    import.path.first().map(String::as_str),
                    Some("core" | "alloc" | "std")
                ) {
                    return Err(StdBundleError::new(
                        edition,
                        vec![format!(
                            "embedded std alias `{}` may target only `core`, `alloc`, or `std`",
                            display_export(module, &name)
                        )],
                    ));
                }
                return Err(StdBundleError::new(
                    edition,
                    vec![format!(
                        "embedded std alias `{}` mirrors another canonical path; reference the target directly",
                        display_export(module, &name)
                    )],
                ));
            }
        }
        exports
            .sort_by(|left, right| (&left.module, &left.name).cmp(&(&right.module, &right.name)));
        for pair in exports.windows(2) {
            if pair[0].module == pair[1].module && pair[0].name == pair[1].name {
                return Err(StdBundleError::new(
                    edition,
                    vec![format!(
                        "embedded std export `{}` is duplicated",
                        display_export(&pair[0].module, &pair[0].name)
                    )],
                ));
            }
        }

        let mut sources = vec![SourceUnit {
            path: "<std>".to_owned(),
            module_path: Vec::new(),
            source: String::new(),
            is_root: true,
        }];
        sources.extend(modules.iter().map(|(module, source)| SourceUnit {
            path: format!("<std/{module}>"),
            module_path: std_source_module_path(module),
            source: (*source).to_owned(),
            is_root: false,
        }));
        let mut program = modules::resolve_embedded_std_sources(&sources)
            .map_err(|diagnostics| StdBundleError::new(edition, diagnostics))?;
        for origin in &mut program.item_origins {
            origin.package = PackageId::STD.0;
            origin.module_path = if origin.module_path.is_empty() {
                vec!["@std".to_owned()]
            } else {
                let mut mapped = vec!["@std".to_owned()];
                mapped.extend(origin.module_path.iter().skip(1).cloned());
                mapped
            };
        }
        Ok(Self { program, exports })
    }

    pub const fn program(&self) -> &Program {
        &self.program
    }

    pub(crate) fn exports(&self) -> &[StdExport] {
        &self.exports
    }
}

fn standard_item_name(item: &Item) -> Option<&str> {
    match item {
        Item::Function(definition) => Some(&definition.name),
        Item::Global(definition) => Some(&definition.name),
        Item::Struct(definition) => Some(&definition.name),
        Item::Enum(definition) => Some(&definition.name),
        Item::Effect(definition) => Some(&definition.name),
        Item::Sort(definition) => Some(&definition.name),
        Item::TypeForm(definition) => Some(&definition.name),
        Item::TypeAlias(definition) => Some(&definition.name),
        Item::Trait(definition) => Some(&definition.name),
        Item::Extend(_) => None,
    }
}

fn std_source_module_path(module: &str) -> Vec<String> {
    match module {
        "lib" => vec!["std".to_owned()],
        module => std::iter::once("std".to_owned())
            .chain(module.split('/').map(str::to_owned))
            .collect(),
    }
}

fn display_export(module: &str, name: &str) -> String {
    if module.is_empty() || module == "lib" {
        format!("std.{name}")
    } else {
        format!("std.{}.{name}", module.replace('/', "."))
    }
}

fn host_target_is_supported(os: &str, arch: &str) -> bool {
    matches!((os, arch), ("linux", "x86_64") | ("macos", "aarch64"))
}

fn validate_host_target(os: &str, arch: &str, edition: Edition) -> Result<(), StdBundleError> {
    if host_target_is_supported(os, arch) {
        Ok(())
    } else {
        Err(StdBundleError::new(
            edition,
            vec![format!(
                "host standard library is unavailable for target `{arch}-{os}`; supported targets are `x86_64-linux` and `aarch64-macos`"
            )],
        ))
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct StdBundleError {
    edition: Edition,
    diagnostics: Vec<String>,
}

impl StdBundleError {
    fn new(edition: Edition, diagnostics: Vec<String>) -> Self {
        Self {
            edition,
            diagnostics,
        }
    }
}

impl fmt::Display for StdBundleError {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(
            formatter,
            "invalid embedded std bundle for edition {}",
            self.edition
        )?;
        for diagnostic in &self.diagnostics {
            write!(formatter, "\n- {diagnostic}")?;
        }
        Ok(())
    }
}

impl Error for StdBundleError {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn edition_2026_std_bundle_owns_policy_without_mirrors() {
        let bundle = StdBundle::for_edition(Edition::Edition2026).unwrap();
        assert!(!bundle.program().items.is_empty());
        assert_eq!(bundle.exports().len(), 42);
        assert!(bundle.exports().iter().any(|export| {
            export.module == "algebra"
                && export.name == "Semigroup"
                && export.target == ["std", "algebra", "Semigroup"]
        }));
        assert!(bundle.exports().iter().any(|export| {
            export.module == "async"
                && export.name == "Spin"
                && export.target == ["std", "async", "Spin"]
        }));
        assert!(bundle.exports().iter().any(|export| {
            export.module == "io" && export.name == "io" && export.target == ["std", "io", "io"]
        }));
        assert!(bundle.exports().iter().any(|export| {
            export.module == "io"
                && export.name == "IoError"
                && export.target == ["std", "io", "IoError"]
        }));
        assert!(bundle.exports().iter().any(|export| {
            export.module == "test"
                && export.name == "assert_eq"
                && export.target == ["std", "test", "assert_eq"]
        }));
        assert!(bundle
            .exports()
            .iter()
            .all(|export| export.target.first().is_some_and(|root| root == "std")));

        let io = bundle
            .program()
            .items
            .iter()
            .find_map(|item| match item {
                Item::Effect(effect) if effect.name == "std::io::io" => Some(effect),
                _ => None,
            })
            .expect("std.io.io must be the canonical embedded authority identity");
        assert!(io.compile_groups.is_empty());
        assert!(io.operations.is_empty());

        let kinds = bundle
            .program()
            .items
            .iter()
            .find_map(|item| match item {
                Item::Enum(definition) if definition.name == "std::io::IoErrorKind" => {
                    Some(definition)
                }
                _ => None,
            })
            .expect("std.io.IoErrorKind must be embedded")
            .variants
            .iter()
            .map(|variant| variant.name.as_str())
            .collect::<Vec<_>>();
        assert_eq!(
            kinds,
            [
                "NotFound",
                "PermissionDenied",
                "AlreadyExists",
                "InvalidInput",
                "InvalidData",
                "Interrupted",
                "WouldBlock",
                "WriteZero",
                "UnexpectedEof",
                "BrokenPipe",
                "Unsupported",
                "OutOfMemory",
                "Other",
            ]
        );
    }

    #[test]
    fn std_bundle_accepts_definitions_and_rejects_mirror_aliases() {
        StdBundle::from_modules(
            Edition::Edition2026,
            &[("owned", "pub let Service = trait {}\n")],
        )
        .unwrap();
        for (source, expected) in [
            (
                "let Option = core.Option.Option",
                "mirrors another canonical path",
            ),
            (
                "pub let Option = core.Option.Option",
                "mirrors another canonical path",
            ),
            (
                "pub let forged = dependency.value",
                "may target only `core`, `alloc`, or `std`",
            ),
        ] {
            let error =
                StdBundle::from_modules(Edition::Edition2026, &[("lib", source)]).unwrap_err();
            assert!(error.to_string().contains(expected), "{error}");
        }
    }

    #[test]
    fn host_std_target_matrix_is_explicit() {
        assert!(host_target_is_supported("linux", "x86_64"));
        assert!(host_target_is_supported("macos", "aarch64"));
        for (os, arch) in [
            ("linux", "aarch64"),
            ("macos", "x86_64"),
            ("windows", "x86_64"),
            ("wasi", "wasm32"),
        ] {
            assert!(!host_target_is_supported(os, arch));
            let error = validate_host_target(os, arch, Edition::Edition2026).unwrap_err();
            assert!(error.to_string().contains(&format!("`{arch}-{os}`")));
        }
    }

    #[test]
    fn standard_names_encode_semantics_instead_of_declaration_categories() {
        let valid = parser::parse(
            "pub let Option = <T: type> enum { Some(T), None }\n\
             pub let Copyable = trait {}\n\
             pub let suspension = effect { suspend(): () }\n",
        )
        .unwrap();
        assert!(naming_diagnostics(&valid, "test").is_empty());

        for (source, expected) in [
            ("pub let network_effect = effect {}\n", "`_effect`"),
            ("pub let Iterator_trait = trait {}\n", "PascalCase"),
            ("pub let message_type = struct {}\n", "PascalCase"),
            ("pub let State = enum { ready }\n", "enum variant"),
            (
                "pub let Iterator = trait { let item: type }\n",
                "associated type",
            ),
            ("pub let Service = { (): () => }\n", "snake_case"),
        ] {
            let program = parser::parse(source).unwrap();
            let diagnostics = naming_diagnostics(&program, "test");
            assert_eq!(diagnostics.len(), 1);
            assert!(diagnostics[0].contains(expected), "{diagnostics:?}");
        }
    }
}

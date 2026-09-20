use super::*;
use crate::ast::GroupDelimiter;

impl Analyzer {
    pub(super) fn resolve_struct_constructor_name(
        &self,
        name: &str,
        context: &LowerCtx,
    ) -> Option<String> {
        if self.collection.struct_layouts.contains_key(name)
            || self.collection.struct_templates.contains_key(name)
        {
            return Some(name.to_owned());
        }
        if context.origin.module_path.is_empty() {
            return None;
        }
        let canonical = format!("{}::{name}", context.origin.module_path.join("::"));
        (self.collection.struct_layouts.contains_key(&canonical)
            || self.collection.struct_templates.contains_key(&canonical))
        .then_some(canonical)
    }

    pub(super) fn lower_struct_construction(
        &mut self,
        constructor: &Expr,
        resolved_name: Option<&str>,
        fields: &[CallArg],
        expected: Option<&Ty>,
        context: &mut LowerCtx,
    ) -> HirExpr {
        let flattened = flatten_call(constructor);
        let root = flattened.root;
        if flattened
            .groups
            .iter()
            .any(|group| group.delimiter != crate::ast::GroupDelimiter::Angle)
        {
            self.error("struct type arguments use `<...>` before `{...}`");
            return error_expr();
        }
        let groups = flattened.argument_groups();
        let Expr::Name(source_name) = root else {
            self.error("struct construction requires a struct type name");
            return error_expr();
        };
        let name = resolved_name.unwrap_or(source_name);
        if context.has_type_parameter(name) {
            self.error(format!(
                "type parameter `{name}` cannot be used as a struct construction head"
            ));
            return error_expr();
        }
        if name == "self" && !context.type_substitutions.contains_key("self") {
            self.error("expression `self` is only available inside an extend member");
            return error_expr();
        }
        if groups.is_empty() && self.collection.struct_layouts.contains_key(name) {
            return self.lower_struct_constructor(name, &[fields], context);
        }
        if self.collection.struct_templates.contains_key(name) {
            let Some(canonical) = self
                .resolve_inferred_generic_struct_instance(name, &groups, fields, expected, context)
            else {
                return error_expr();
            };
            return self.lower_struct_constructor(&canonical, &[fields], context);
        }
        if self.collection.enum_layouts.contains_key(name)
            || self.collection.enum_templates.contains_key(name)
        {
            self.error(format!(
                "brace construction `{name}{{ ... }}` requires a struct type, found enum `{name}`"
            ));
            return error_expr();
        }
        if self.collection.struct_layouts.contains_key(name) {
            self.error(format!(
                "struct `{name}` does not accept these type argument groups in brace construction"
            ));
            return error_expr();
        }
        self.error(format!("unknown struct `{name}`"));
        error_expr()
    }

    pub(super) fn lower_struct_constructor(
        &mut self,
        name: &str,
        groups: &[&[CallArg]],
        context: &mut LowerCtx,
    ) -> HirExpr {
        if groups.len() != 1 {
            self.error(format!(
                "struct constructor `{name}` expects exactly one argument group"
            ));
            return error_expr();
        }
        let Some(layout) = self.struct_layout_or_diagnostic(name) else {
            return error_expr();
        };
        let mut accessible = true;
        for field in &layout.fields {
            accessible &= self.require_field_access(name, field, &context.origin);
        }
        if !accessible {
            return error_expr();
        }
        let fields = self.lower_constructor_fields(
            groups[0],
            &layout.fields,
            true,
            GroupDelimiter::Brace,
            &format!("struct `{name}`"),
            context,
        );
        HirExpr {
            ty: Ty::Struct(name.to_owned()),
            kind: HirExprKind::ConstructStruct {
                name: name.to_owned(),
                fields,
            },
        }
    }

    pub(super) fn lower_enum_constructor(
        &mut self,
        enum_name: &str,
        variant: usize,
        groups: &[&[CallArg]],
        actual_delimiters: Option<&[GroupDelimiter]>,
        context: &mut LowerCtx,
    ) -> HirExpr {
        if groups.len() != 1 {
            self.error(format!(
                "enum variant constructor `{enum_name}` expects exactly one argument group"
            ));
            return error_expr();
        }
        let Some(layout) = self.enum_layout_or_diagnostic(enum_name) else {
            return error_expr();
        };
        let variant_layout = &layout.variants[variant];
        let expected_delimiter = if variant_layout.named {
            GroupDelimiter::Brace
        } else {
            GroupDelimiter::Parenthesis
        };
        if actual_delimiters.is_some_and(|actual| actual != [expected_delimiter]) {
            self.error(format!(
                "enum variant constructor `{enum_name}.{}` must use `{}`",
                variant_layout.name,
                expected_delimiter.opening()
            ));
            return error_expr();
        }
        if variant_layout.fields.is_empty() {
            self.error(format!(
                "unit variant `{enum_name}.{}` is a value and must not be called",
                variant_layout.name
            ));
            return error_expr();
        }
        let owner = format!("{enum_name}.{}", variant_layout.name);
        let mut accessible = true;
        for field in &variant_layout.fields {
            accessible &= self.require_field_access(&owner, field, &context.origin);
        }
        if !accessible {
            return error_expr();
        }
        let fields = self.lower_constructor_fields(
            groups[0],
            &variant_layout.fields,
            variant_layout.named,
            expected_delimiter,
            &format!("variant `{enum_name}.{}`", variant_layout.name),
            context,
        );
        HirExpr {
            ty: Ty::Enum(enum_name.to_owned()),
            kind: HirExprKind::ConstructEnum {
                name: enum_name.to_owned(),
                variant,
                fields,
            },
        }
    }

    pub(super) fn lower_constructor_fields(
        &mut self,
        arguments: &[CallArg],
        fields: &[FieldLayout],
        labels_allowed: bool,
        delimiter: GroupDelimiter,
        constructor: &str,
        context: &mut LowerCtx,
    ) -> Vec<(usize, HirExpr)> {
        if !labels_allowed && arguments.iter().any(|argument| argument.label.is_some()) {
            self.error(format!("{constructor} does not accept labeled arguments"));
            return Vec::new();
        }
        let parameters = fields
            .iter()
            .map(|field| ParamSig {
                name: field.name.clone(),
                ty: field.ty.clone(),
                mode: PassMode::Inferred,
            })
            .collect::<Vec<_>>();
        for (index, argument) in arguments.iter().enumerate() {
            if let Some(label) = argument.label.as_deref() {
                if arguments[..index]
                    .iter()
                    .any(|previous| previous.label.as_deref() == Some(label))
                {
                    self.error(format!("duplicate field `{label}` in {constructor}"));
                }
            }
        }
        let Some(arguments) =
            self.elaborate_runtime_group(constructor, 1, delimiter, arguments, &parameters)
        else {
            return Vec::new();
        };
        arguments
            .iter()
            .zip(fields)
            .enumerate()
            .map(|(index, (argument, field))| {
                let value = match (&field.ty, argument.value.unlocated()) {
                    (Ty::Function(function), Expr::Closure(parameters, body)) => self
                        .lower_noncapturing_closure_argument_as_function(
                            parameters,
                            body,
                            function,
                            &field.name,
                            context,
                        ),
                    _ => self.lower_expr(&argument.value, Some(&field.ty), context),
                };
                (index, value)
            })
            .collect()
    }

    pub(super) fn resolve_short_variant(
        &mut self,
        name: &str,
        expected: Option<&Ty>,
        origin: &ItemOrigin,
    ) -> Option<(String, usize)> {
        if let Some(Ty::Enum(enum_name)) = expected {
            let layout = self.enum_layout_or_diagnostic(enum_name)?;
            let enum_is_accessible = self
                .collection
                .nominal_accesses
                .get(enum_name)
                .is_some_and(|access| Self::access_boundary_allows(origin, access));
            if enum_is_accessible {
                if let Some(index) = layout
                    .variants
                    .iter()
                    .position(|variant| variant.name == name)
                {
                    return Some((enum_name.clone(), index));
                }
            }
        }
        let candidates: Vec<_> = self
            .collection
            .enum_layouts
            .iter()
            .filter_map(|(enum_name, layout)| {
                let is_non_generic = self
                    .collection
                    .nominal_instances
                    .get(enum_name)
                    .is_some_and(|instance| instance.key.arguments.is_empty());
                if !is_non_generic {
                    return None;
                }
                if !self
                    .collection
                    .nominal_accesses
                    .get(enum_name)
                    .is_some_and(|access| Self::access_boundary_allows(origin, access))
                {
                    return None;
                }
                layout
                    .variants
                    .iter()
                    .position(|variant| variant.name == name)
                    .map(|variant| (enum_name.clone(), variant))
            })
            .collect();
        match candidates.as_slice() {
            [candidate] => Some(candidate.clone()),
            [] => None,
            _ => {
                self.error(format!(
                    "variant name `{name}` is ambiguous; qualify it with its enum"
                ));
                None
            }
        }
    }
}

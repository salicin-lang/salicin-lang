use crate::ast::{CallArg, CallGroup, Expr, GroupDelimiter, PassMode, Type};
use crate::core::LangItemKind;

use super::fallible::InferredEnumHints;
use super::flow::{LowerCtx, RecursiveFrameCall};
use super::hir::{
    ContinuationAdapter, EffectCallableAdapter, HirExpr, HirExprKind, HirPlace, LayoutQueryKind,
    LocalCapability, ParamSig, Ty,
};
use super::lower::{error_expr, flatten_call, BoundMethodConstraint, TypeProbe};
use super::registry::NominalKind;
use super::Analyzer;

fn normalized_group_delimiters(
    delimiters: &[GroupDelimiter],
    group_count: usize,
) -> Vec<GroupDelimiter> {
    (0..group_count)
        .map(|index| {
            delimiters
                .get(index)
                .copied()
                .unwrap_or(GroupDelimiter::Parenthesis)
        })
        .collect()
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub(super) struct CallableBridgeKey {
    pub(super) callee: String,
    pub(super) group: usize,
    pub(super) parameter: usize,
    pub(super) closure_shape: String,
    pub(super) captures: Vec<(PassMode, Type)>,
}

#[derive(Debug, Clone)]
pub(super) struct CallableBridgeSpecialization {
    pub(super) canonical: String,
    pub(super) lifted_parameters: Vec<String>,
}

pub(super) fn rewrite_callable_bridge_groups(
    groups: &[&[CallArg]],
    group_index: usize,
    parameter_index: usize,
    lifted_parameters: &[String],
    captures: &[(String, PassMode, Type)],
) -> Vec<Vec<CallArg>> {
    let mut rewritten = groups
        .iter()
        .map(|group| group.to_vec())
        .collect::<Vec<_>>();
    let labeled = rewritten[group_index]
        .iter()
        .all(|argument| argument.label.is_some());
    rewritten[group_index].remove(parameter_index);
    for (offset, (lifted, (source, _, _))) in lifted_parameters.iter().zip(captures).enumerate() {
        rewritten[group_index].insert(
            parameter_index + offset,
            CallArg {
                label: labeled.then(|| lifted.clone()),
                value: Expr::Name(source.clone()),
            },
        );
    }
    rewritten
}

impl Analyzer {
    pub(super) fn elaborate_runtime_group(
        &mut self,
        owner: &str,
        group_number: usize,
        delimiter: GroupDelimiter,
        arguments: &[CallArg],
        parameters: &[ParamSig],
    ) -> Option<Vec<CallArg>> {
        let mut arguments = arguments.to_vec();
        if delimiter == GroupDelimiter::Brace {
            if parameters.is_empty()
                && matches!(arguments.as_slice(), [argument] if is_empty_brace_body(&argument.value))
            {
                arguments.clear();
            } else if let ([argument], [parameter]) = (arguments.as_mut_slice(), parameters) {
                if !matches!(parameter.ty, Ty::Function(_) | Ty::Callable(_)) {
                    if let Some(body) = brace_body_value(&argument.value) {
                        argument.value = body.clone();
                    }
                }
            }
        }
        let names = parameters
            .iter()
            .map(|parameter| parameter.name.clone())
            .collect::<Vec<_>>();
        self.ordered_call_arguments(owner, group_number, &arguments, &names)
            .map(|ordered| ordered.into_iter().cloned().collect())
    }

    pub(super) fn lower_call(
        &mut self,
        expression: &Expr,
        expected: Option<&Ty>,
        context: &mut LowerCtx,
    ) -> HirExpr {
        let flattened = flatten_call(expression);
        let root = flattened.root;
        let groups = flattened.argument_groups();
        let actual_delimiters = flattened
            .groups
            .iter()
            .map(|group| group.delimiter)
            .collect::<Vec<_>>();
        if let Expr::Name(name) = root {
            if !self.explicit_compile_delimiters_match(
                name,
                &groups,
                &actual_delimiters,
                context,
            ) {
                return error_expr();
            }
            let local_expected = context.lookup(name).and_then(|local| {
                if let Some(partial) = &local.partial {
                    self.collection
                        .functions
                        .get(&partial.function)
                        .or_else(|| self.collection.function_templates.get(&partial.function))
                        .map(|function| {
                            let group_count = self
                                .lowering
                                .signatures
                                .get(&partial.function)
                                .map_or(function.groups.len(), |signature| signature.groups.len())
                                .max(usize::from(partial.function.starts_with("$trait$impl$")));
                            normalized_group_delimiters(
                                &function.effects.group_delimiters,
                                group_count,
                            )
                                .into_iter()
                                .skip(partial.consumed_groups)
                                .collect::<Vec<_>>()
                        })
                } else {
                    match &local.ty {
                        Ty::Function(function) => Some(normalized_group_delimiters(
                            &function.group_delimiters,
                            function.groups.len(),
                        )),
                        Ty::Callable(callable) => Some(normalized_group_delimiters(
                            &callable.signature.group_delimiters,
                            callable.signature.groups.len(),
                        )),
                        _ => None,
                    }
                }
            });
            let function_expected = self
                .collection
                .functions
                .get(name)
                .or_else(|| self.collection.function_templates.get(name))
                .map(|function| {
                    let group_count = self
                        .lowering
                        .signatures
                        .get(name)
                        .map_or(function.groups.len(), |signature| signature.groups.len())
                        .max(usize::from(name.starts_with("$trait$impl$")));
                    normalized_group_delimiters(
                        &function.effects.group_delimiters,
                        group_count,
                    )
                });
            let compile_capacity = self.named_compile_group_capacity(name).unwrap_or(0);
            let compile_prefix = actual_delimiters
                .iter()
                .take_while(|delimiter| **delimiter == GroupDelimiter::Angle)
                .count()
                .min(compile_capacity);
            if let Some(expected) = local_expected.or(function_expected) {
                if !self.call_delimiters_match(
                    name,
                    &actual_delimiters[compile_prefix..],
                    &expected,
                ) {
                    return error_expr();
                }
            }
        }
        if let Expr::Name(name) = root {
            if self.is_lang_item_name(name, LangItemKind::If) {
                return self.lower_if_match_call(&groups, expected, context);
            }
            if name == "$handler$erase$continuation" {
                if groups.len() != 1 || groups[0].len() != 1 {
                    self.error("internal continuation erasure expects one callable argument");
                    return error_expr();
                }
                let Expr::Name(local_name) = &groups[0][0].value else {
                    self.error("internal continuation erasure requires a local closure");
                    return error_expr();
                };
                let Some(local) = context.lookup(local_name).cloned() else {
                    self.error("internal continuation erasure refers to an unknown closure");
                    return error_expr();
                };
                let Some(closure) = local.closure.clone() else {
                    self.error("internal continuation erasure requires closure metadata");
                    return error_expr();
                };
                let [group] = closure.groups.as_slice() else {
                    self.error("an erased continuation must have one parameter group");
                    return error_expr();
                };
                let [parameter] = group.as_slice() else {
                    self.error("an erased continuation must accept exactly one value");
                    return error_expr();
                };
                let callable = HirPlace {
                    local: local.id,
                    root_ty: local.ty.clone(),
                    projections: Vec::new(),
                    dynamic_index: None,
                    ty: local.ty.clone(),
                    capability: LocalCapability::Owned,
                    root_mutable: local.mutable,
                    loan: None,
                    indirect: false,
                };
                self.ensure_available(&callable, context);
                self.mark_moved(&callable, context);
                let continuation_ty = Ty::Continuation {
                    input: Box::new(parameter.ty.clone()),
                    output: Box::new(closure.result.clone()),
                };
                let adapter = format!("$continuation$adapter${}", closure.function);
                if !self
                    .lowering
                    .continuation_adapters
                    .iter()
                    .any(|existing| existing.name == adapter)
                {
                    let Ty::Callable(callable_ty) = &local.ty else {
                        self.error("internal continuation closure has no callable type");
                        return error_expr();
                    };
                    self.lowering
                        .continuation_adapters
                        .push(ContinuationAdapter {
                            name: adapter.clone(),
                            callable_ty: local.ty.clone(),
                            function: closure.function,
                            captures: callable_ty.captures.clone(),
                            input: parameter.ty.clone(),
                            output: closure.result,
                        });
                }
                return HirExpr {
                    ty: continuation_ty,
                    kind: HirExprKind::EraseContinuation {
                        binding: local.id,
                        callable_ty: local.ty,
                        adapter,
                    },
                };
            }
            if name == "$handler$invoke$continuation" {
                if groups.len() != 1 || groups[0].len() != 2 {
                    self.error("internal continuation invocation expects continuation and value");
                    return error_expr();
                }
                let continuation = self.lower_expr(&groups[0][0].value, None, context);
                let Ty::Continuation { input, output } = continuation.ty.clone() else {
                    self.error("internal continuation invocation requires an erased continuation");
                    return error_expr();
                };
                let argument = self.lower_expr(&groups[0][1].value, Some(&input), context);
                self.require_same_type(&argument.ty, &input, "continuation input");
                return HirExpr {
                    ty: (*output).clone(),
                    kind: HirExprKind::InvokeContinuation {
                        continuation: Box::new(continuation),
                        argument: Box::new(argument),
                    },
                };
            }
            if name == "$handler$erase$effect$callable" {
                if groups.len() != 1 || groups[0].len() != 1 {
                    self.error("internal effect-callable erasure expects one callable argument");
                    return error_expr();
                }
                let Expr::Name(local_name) = &groups[0][0].value else {
                    self.error("internal effect-callable erasure requires a local closure");
                    return error_expr();
                };
                let Some(local) = context.lookup(local_name).cloned() else {
                    self.error("internal effect-callable erasure refers to an unknown closure");
                    return error_expr();
                };
                let Some(closure) = local.closure.clone() else {
                    self.error("internal effect-callable erasure requires closure metadata");
                    return error_expr();
                };
                let [group] = closure.groups.as_slice() else {
                    self.error("an erased effect callable must have one parameter group");
                    return error_expr();
                };
                let (input, continuation_parameter) = match group.as_slice() {
                    [continuation] => (Ty::Unit, continuation),
                    [input, continuation] => (input.ty.clone(), continuation),
                    _ => {
                        self.error(
                            "an erased effect callable must accept an optional input and a continuation",
                        );
                        return error_expr();
                    }
                };
                let Ty::Continuation {
                    input: output,
                    output: answer,
                } = &continuation_parameter.ty
                else {
                    self.error(
                        "an erased effect callable's second parameter must be a continuation",
                    );
                    return error_expr();
                };
                self.require_same_type(&closure.result, answer, "erased effect-callable answer");
                let callable = HirPlace {
                    local: local.id,
                    root_ty: local.ty.clone(),
                    projections: Vec::new(),
                    dynamic_index: None,
                    ty: local.ty.clone(),
                    capability: LocalCapability::Owned,
                    root_mutable: local.mutable,
                    loan: None,
                    indirect: false,
                };
                self.ensure_available(&callable, context);
                self.mark_moved(&callable, context);
                let action_ty = Ty::EffectCallable {
                    input: Box::new(input.clone()),
                    output: output.clone(),
                    answer: answer.clone(),
                };
                let adapter = format!("$effect$callable$adapter${}", closure.function);
                if !self
                    .lowering
                    .effect_callable_adapters
                    .iter()
                    .any(|existing| existing.name == adapter)
                {
                    let Ty::Callable(callable_ty) = &local.ty else {
                        self.error("internal effect closure has no callable type");
                        return error_expr();
                    };
                    self.lowering
                        .effect_callable_adapters
                        .push(EffectCallableAdapter {
                            name: adapter.clone(),
                            callable_ty: local.ty.clone(),
                            function: closure.function,
                            captures: callable_ty.captures.clone(),
                            input,
                            output: (**output).clone(),
                            answer: (**answer).clone(),
                        });
                }
                return HirExpr {
                    ty: action_ty,
                    kind: HirExprKind::EraseEffectCallable {
                        binding: local.id,
                        callable_ty: local.ty,
                        adapter,
                    },
                };
            }
            if name == "$handler$invoke$effect$callable" {
                if groups.len() != 1 || groups[0].len() != 3 {
                    self.error(
                        "internal effect-callable invocation expects action, input, and continuation",
                    );
                    return error_expr();
                }
                let action = self.lower_expr(&groups[0][0].value, None, context);
                let Ty::EffectCallable {
                    input,
                    output,
                    answer,
                } = action.ty.clone()
                else {
                    self.error("internal effect-callable invocation requires an erased action");
                    return error_expr();
                };
                let input_value = self.lower_expr(&groups[0][1].value, Some(&input), context);
                self.require_same_type(&input_value.ty, &input, "effect-callable input");
                let expected_continuation = Ty::Continuation {
                    input: output,
                    output: answer.clone(),
                };
                let continuation =
                    self.lower_expr(&groups[0][2].value, Some(&expected_continuation), context);
                self.require_same_type(
                    &continuation.ty,
                    &expected_continuation,
                    "effect-callable continuation",
                );
                return HirExpr {
                    ty: (*answer).clone(),
                    kind: HirExprKind::InvokeEffectCallable {
                        action: Box::new(action),
                        input: Box::new(input_value),
                        continuation: Box::new(continuation),
                    },
                };
            }
            if name.starts_with("$handler$tail$") {
                if groups.len() != 1 || groups[0].len() != 1 {
                    self.error("internal handler tail continuation has invalid arguments");
                    return error_expr();
                }
                if let Some(boundary) = context.return_boundary.clone() {
                    let call =
                        self.lower_expr(&groups[0][0].value, Some(&boundary.success), context);
                    let result = self.finish_return_value(call, &boundary);
                    context.returned_types.push(result.ty.clone());
                    context.flow.reachable = false;
                    return HirExpr {
                        ty: Ty::Never,
                        kind: HirExprKind::Return(Some(Box::new(result))),
                    };
                }
                let declared_result = context.declared_result.clone();
                let call = self.lower_expr(&groups[0][0].value, declared_result.as_ref(), context);
                let result = call.ty.clone();
                if let Some(expected) = context.declared_result.clone() {
                    self.require_same_type(&result, &expected, "handler tail continuation result");
                }
                context.returned_types.push(result.clone());
                context.flow.reachable = false;
                return match call.kind {
                    HirExprKind::Call {
                        function,
                        arguments,
                        consumed_callable,
                        ..
                    } => HirExpr {
                        ty: Ty::Never,
                        kind: HirExprKind::TailCall {
                            function,
                            arguments,
                            consumed_callable,
                            result,
                        },
                    },
                    HirExprKind::InvokeContinuation {
                        continuation,
                        argument,
                    } => HirExpr {
                        ty: Ty::Never,
                        kind: HirExprKind::TailInvokeContinuation {
                            continuation,
                            argument,
                            result,
                        },
                    },
                    _ => {
                        self.error(
                            "handler tail continuation must resolve to a direct or erased continuation call",
                        );
                        error_expr()
                    }
                };
            }
            if let Some(frame) = context.recursive_frame_calls.get(name).cloned() {
                return self.lower_recursive_frame_call(&frame, &groups, context);
            }
        }
        if let Expr::ChainMember(base, member) = root {
            let groups = flattened
                .groups
                .iter()
                .map(|group| CallGroup {
                    delimiter: group.delimiter,
                    arguments: group.arguments.to_vec(),
                })
                .collect::<Vec<_>>();
            return self.lower_chain(base, member, Some(&groups), expected, context);
        }
        if let Expr::Name(name) = root {
            if self.is_lang_item_name(name, LangItemKind::SizeOf) {
                return self.lower_layout_query(LayoutQueryKind::Size, &groups, context);
            }
            if self.is_lang_item_name(name, LangItemKind::AlignOf) {
                return self.lower_layout_query(LayoutQueryKind::Align, &groups, context);
            }
            if name == "raw_alloc" {
                return self.lower_raw_alloc(&groups, expected, context);
            }
            if name == "raw_dealloc" {
                return self.lower_raw_dealloc(&groups, context);
            }
            if name == "raw_init" {
                return self.lower_raw_init(&groups, context);
            }
            if name == "raw_take" {
                return self.lower_raw_take(&groups, context);
            }
            if name == "raw_offset" {
                return self.lower_raw_offset(&groups, context);
            }
            if name == "raw_borrow" {
                return self.lower_raw_borrow(name, &groups, expected, context);
            }
            if name == "raw_slice" {
                return self.lower_raw_slice(&groups, expected, context);
            }
            if name == "raw_array_slice" {
                return self.lower_raw_array_slice(&groups, expected, context);
            }
            if name == "raw_slice_len" {
                return self.lower_raw_slice_len(&groups, context);
            }
            if name == "raw_slice_ptr" {
                if !matches!(
                    actual_delimiters.as_slice(),
                    [GroupDelimiter::Parenthesis]
                        | [GroupDelimiter::Angle, GroupDelimiter::Parenthesis]
                ) {
                    self.error(
                        "`raw_slice_ptr` expects an optional `<access>` compile-time group followed by a parenthesized runtime group",
                    );
                    return error_expr();
                }
                return self.lower_raw_slice_ptr(&groups, context);
            }
            if name == "raw_slice_at" {
                return self.lower_raw_slice_at(&groups, expected, context);
            }
            if name == "raw_subview" {
                return self.lower_raw_subview(&groups, context);
            }
            if name == "raw_str" || name == "raw_str_bytes" {
                return self.lower_raw_str_cast(name, &groups, expected, context);
            }
            if name == "raw_trap" {
                return self.lower_raw_trap(&groups, context);
            }
            if name == "forget" {
                return self.lower_forget(&groups, context);
            }
            if self.is_lang_item_name(name, LangItemKind::PtrValueForm) {
                return self.lower_raw_pointer_constructor(&groups, context);
            }
            if let Some(local) = context.lookup(name).cloned() {
                if local.closure.is_some() {
                    return self.lower_local_closure_call(name, &local, &groups, expected, context);
                }
                if local.partial.is_some() {
                    return self.lower_local_partial_call(name, &local, &groups, expected, context);
                }
                if matches!(local.ty, Ty::Function(_)) {
                    return self
                        .lower_indirect_function_call(name, &local, &groups, expected, context);
                }
                let has_top_level_callable = self.collection.function_overloads.contains_key(name)
                    || self.collection.function_templates.contains_key(name)
                    || self.collection.functions.contains_key(name);
                if !has_top_level_callable {
                    self.error(format!("local value `{name}` is not callable"));
                    return error_expr();
                }
            }
            if context.has_type_parameter(name) {
                self.error(format!("type parameter `{name}` is not callable"));
                return error_expr();
            }
            if name == "self" {
                self.error("expression `self` is only available inside an extend member");
                return error_expr();
            }
            if self.collection.function_overloads.contains_key(name) {
                let Some(selected) = self.resolve_function_overload(name, &groups) else {
                    return error_expr();
                };
                if !self.named_call_delimiters_match(
                    &selected,
                    &groups,
                    &actual_delimiters,
                    context,
                ) {
                    return error_expr();
                }
                if self.collection.function_templates.contains_key(&selected) {
                    let explicit = actual_delimiters
                        .iter()
                        .take_while(|delimiter| **delimiter == GroupDelimiter::Angle)
                        .count();
                    return self.lower_generic_function_call(
                        &selected,
                        &groups,
                        Some(explicit),
                        expected,
                        context,
                    );
                }
                return self.lower_named_function_call(&selected, &groups, expected, context);
            }
            if self.collection.function_templates.contains_key(name) {
                let explicit = actual_delimiters
                    .iter()
                    .take_while(|delimiter| **delimiter == GroupDelimiter::Angle)
                    .count();
                return self.lower_generic_function_call(
                    name,
                    &groups,
                    Some(explicit),
                    expected,
                    context,
                );
            }
            if self.collection.functions.contains_key(name) {
                return self.lower_named_function_call(name, &groups, expected, context);
            }
            if self.collection.struct_layouts.contains_key(name) {
                self.error(format!(
                    "struct `{name}` is not callable; construct it with `{name}{{ ... }}`"
                ));
                return error_expr();
            }
            if self.collection.struct_templates.contains_key(name) {
                self.error(format!(
                    "generic struct `{name}` is not callable; construct it with `{name}<...>{{ ... }}` or `{name}{{ ... }}`"
                ));
                return error_expr();
            }
            if self.collection.enum_templates.contains_key(name) {
                self.error(format!(
                    "generic enum type `{name}` is not directly callable; select a variant"
                ));
                return error_expr();
            }
            if let Some((enum_name, variant)) =
                self.resolve_short_variant(name, expected, &context.origin)
            {
                return self.lower_enum_constructor(
                    &enum_name,
                    variant,
                    &groups,
                    Some(&actual_delimiters),
                    context,
                );
            }
            self.error(format!("`{name}` is not a function or constructor"));
            return error_expr();
        }
        if let Expr::Member(base, variant_name) = root {
            match self.resolve_effect_application(base, context) {
                Ok(Some((definition, instance))) => {
                    if variant_name == "handle" {
                        if actual_delimiters.as_slice() != [GroupDelimiter::Brace] {
                            self.error(format!(
                                "`{}.handle` expects one brace-delimited argument group",
                                super::compile_time::source_effect_identity(&instance)
                            ));
                            return error_expr();
                        }
                        return self.lower_effect_handler(
                            &definition,
                            &instance,
                            &groups,
                            expected,
                            context,
                        );
                    }
                    return self.lower_effect_operation_call(
                        &definition,
                        &instance,
                        variant_name,
                        &groups,
                        Some(&actual_delimiters),
                        expected,
                        context,
                    );
                }
                Err(()) => return error_expr(),
                Ok(None) => {}
            }
            if let Some((member, lang_item)) = match variant_name.as_str() {
                "$lang$into_iter" => Some(("into_iter", LangItemKind::IntoIterator)),
                "$lang$next" => Some(("next", LangItemKind::Iterator)),
                "$lang$chain" => Some(("chain", LangItemKind::Chain)),
                "$lang$coalesce" => Some(("coalesce", LangItemKind::Coalesce)),
                "$lang$unwrap" => Some(("unwrap", LangItemKind::Unwrap)),
                "$lang$raise" => Some(("raise", LangItemKind::Raise)),
                _ => None,
            } {
                let groups = if matches!(lang_item, LangItemKind::Unwrap | LangItemKind::Raise)
                    && matches!(groups.as_slice(), [group] if group.is_empty())
                {
                    &groups[..0]
                } else {
                    &groups
                };
                return self.lower_bound_method_call(
                    base,
                    member,
                    groups,
                    Some(&actual_delimiters),
                    BoundMethodConstraint::LangItem(lang_item),
                    if lang_item == LangItemKind::Raise {
                        None
                    } else {
                        expected
                    },
                    context,
                );
            }
            if let Some((name, type_groups)) = self.inferred_generic_enum_type_head(base, context) {
                let is_variant = self.collection.enum_templates[&name]
                    .variants
                    .iter()
                    .any(|variant| variant.name == *variant_name);
                if is_variant {
                    let Some(canonical) = self.resolve_inferred_generic_enum_instance(
                        &name,
                        &type_groups,
                        variant_name,
                        &groups,
                        InferredEnumHints {
                            payload: None,
                            result: expected,
                        },
                        context,
                    ) else {
                        return error_expr();
                    };
                    return self.lower_nominal_type_member_call(
                        &canonical,
                        NominalKind::Enum,
                        variant_name,
                        &groups,
                        Some(&actual_delimiters),
                        expected,
                        context,
                    );
                }
            }
            if let Expr::Name(target_template) = base.as_ref() {
                let explicit_method = groups.first().is_some_and(|group| {
                    matches!(*group, [CallArg { label: Some(label), .. }] if label == "self")
                        || (groups.len() > 1
                            && group.iter().all(|argument| argument.label.is_none()))
                });
                let has_static_member = self
                    .collection
                    .inherent_members
                    .get(target_template)
                    .is_some_and(|members| {
                        members.functions.contains_key(variant_name)
                            || members.constants.contains_key(variant_name)
                    })
                    || self
                        .collection
                        .enum_layouts
                        .get(target_template)
                        .is_some_and(|layout| {
                            layout
                                .variants
                                .iter()
                                .any(|variant| variant.name == *variant_name)
                        })
                    || self
                        .collection
                        .generic_inherent_functions
                        .contains_key(&(target_template.clone(), variant_name.clone()))
                    || self.collection.inherent_overloads.contains_key(&(
                        target_template.clone(),
                        variant_name.clone(),
                        false,
                    ));
                if !context.shadows_top_level_name(target_template)
                    || has_static_member
                    || explicit_method
                {
                    if !explicit_method {
                        let overload_key = (target_template.clone(), variant_name.clone(), false);
                        if (self
                            .collection
                            .struct_templates
                            .contains_key(target_template)
                            || self.collection.enum_templates.contains_key(target_template))
                            && self
                                .collection
                                .inherent_overloads
                                .contains_key(&overload_key)
                        {
                            let Some(canonical) = self.resolve_inherent_overload(
                                target_template,
                                variant_name,
                                false,
                                &groups,
                            ) else {
                                return error_expr();
                            };
                            if !self.named_call_delimiters_match(
                                &canonical,
                                &groups,
                                &actual_delimiters,
                                context,
                            ) {
                                return error_expr();
                            }
                            return self.lower_generic_function_call(
                                &canonical,
                                &groups,
                                Some(
                                    actual_delimiters
                                        .iter()
                                        .take_while(|delimiter| {
                                            **delimiter == GroupDelimiter::Angle
                                        })
                                        .count(),
                                ),
                                expected,
                                context,
                            );
                        }
                        if let Some(canonical) = self
                            .collection
                            .generic_inherent_functions
                            .get(&(target_template.clone(), variant_name.clone()))
                            .cloned()
                        {
                            if !self.named_call_delimiters_match(
                                &canonical,
                                &groups,
                                &actual_delimiters,
                                context,
                            ) {
                                return error_expr();
                            }
                            return self.lower_generic_function_call(
                                &canonical,
                                &groups,
                                Some(
                                    actual_delimiters
                                        .iter()
                                        .take_while(|delimiter| {
                                            **delimiter == GroupDelimiter::Angle
                                        })
                                        .count(),
                                ),
                                expected,
                                context,
                            );
                        }
                        if let Some(canonical) = (!self
                            .collection
                            .inherent_overloads
                            .contains_key(&overload_key))
                        .then(|| {
                            self.collection
                                .inherent_members
                                .get(target_template)
                                .and_then(|members| members.functions.get(variant_name))
                                .cloned()
                        })
                        .flatten()
                        {
                            if !self.named_call_delimiters_match(
                                &canonical,
                                &groups,
                                &actual_delimiters,
                                context,
                            ) {
                                return error_expr();
                            }
                            return self
                                .lower_named_function_call(&canonical, &groups, expected, context);
                        }
                        if self
                            .collection
                            .struct_templates
                            .contains_key(target_template)
                            || self.collection.enum_templates.contains_key(target_template)
                        {
                            if let Some(result) = self
                                .lower_constructor_trait_associated_function_call(
                                    target_template,
                                    variant_name,
                                    &groups,
                                    expected,
                                    context,
                                )
                            {
                                return result;
                            }
                        }
                    }
                    if self
                        .collection
                        .struct_templates
                        .contains_key(target_template)
                        || self.collection.enum_templates.contains_key(target_template)
                    {
                        if let Some([receiver]) = groups.first().copied() {
                            let receiver_ty =
                                match self.probe_expr_ty(&receiver.value, None, context) {
                                    TypeProbe::Known(ty) | TypeProbe::KnownSource(ty, _) => {
                                        Some(ty)
                                    }
                                    TypeProbe::Defaultable(_) | TypeProbe::Unsupported => None,
                                };
                            if let Some((canonical, kind)) = receiver_ty.and_then(|ty| match ty {
                                Ty::Struct(name) => Some((name, NominalKind::Struct)),
                                Ty::Enum(name) => Some((name, NominalKind::Enum)),
                                _ => None,
                            }) {
                                let belongs_to_template = self
                                    .collection
                                    .nominal_instances
                                    .get(&canonical)
                                    .is_some_and(|instance| {
                                        instance.key.template == *target_template
                                            && instance.key.kind == kind
                                    });
                                let self_ty = match kind {
                                    NominalKind::Struct => Ty::Struct(canonical.clone()),
                                    NominalKind::Enum => Ty::Enum(canonical.clone()),
                                };
                                let has_method = self
                                    .collection
                                    .inherent_members
                                    .get(&canonical)
                                    .is_some_and(|members| {
                                        members.methods.contains_key(variant_name)
                                    })
                                    || !self
                                        .trait_method_candidates(
                                            &self_ty,
                                            variant_name,
                                            &context.origin,
                                        )
                                        .is_empty();
                                if belongs_to_template && has_method {
                                    return self.lower_nominal_type_member_call(
                                        &canonical,
                                        kind,
                                        variant_name,
                                        &groups,
                                        Some(&actual_delimiters),
                                        expected,
                                        context,
                                    );
                                }
                            }
                        }
                    }
                }
            }
            match self.resolve_nominal_type_head(base, context) {
                Ok(Some((target, kind))) => {
                    return self.lower_nominal_type_member_call(
                        &target,
                        kind,
                        variant_name,
                        &groups,
                        Some(&actual_delimiters),
                        expected,
                        context,
                    );
                }
                Err(()) => return error_expr(),
                Ok(None) => {}
            }
            if let Expr::Name(enum_name) = base.as_ref() {
                let has_static_member = self
                    .collection
                    .inherent_members
                    .get(enum_name)
                    .is_some_and(|members| {
                        members.functions.contains_key(variant_name)
                            || members.constants.contains_key(variant_name)
                    })
                    || self
                        .collection
                        .enum_layouts
                        .get(enum_name)
                        .is_some_and(|layout| {
                            layout
                                .variants
                                .iter()
                                .any(|variant| variant.name == *variant_name)
                        });
                if (!context.shadows_top_level_name(enum_name) || has_static_member)
                    && (self.collection.struct_layouts.contains_key(enum_name)
                        || self.collection.enum_layouts.contains_key(enum_name))
                {
                    if let Some(canonical) = self
                        .collection
                        .inherent_members
                        .get(enum_name)
                        .and_then(|members| members.functions.get(variant_name))
                        .cloned()
                    {
                        if !self.named_call_delimiters_match(
                            &canonical,
                            &groups,
                            &actual_delimiters,
                            context,
                        ) {
                            return error_expr();
                        }
                        return self
                            .lower_named_function_call(&canonical, &groups, expected, context);
                    }
                    if self
                        .collection
                        .inherent_members
                        .get(enum_name)
                        .is_some_and(|members| members.constants.contains_key(variant_name))
                    {
                        self.error(format!(
                            "associated constant `{enum_name}.{variant_name}` is not callable"
                        ));
                        return error_expr();
                    }
                }
                if !context.shadows_top_level_name(enum_name) || has_static_member {
                    if let Some(layout) = self.collection.enum_layouts.get(enum_name) {
                        if let Some(variant) = layout
                            .variants
                            .iter()
                            .position(|variant| variant.name == *variant_name)
                        {
                            return self
                                .lower_enum_constructor(
                                    enum_name,
                                    variant,
                                    &groups,
                                    Some(&actual_delimiters),
                                    context,
                                );
                        }
                        if self
                            .collection
                            .inherent_members
                            .get(enum_name)
                            .is_some_and(|members| members.methods.contains_key(variant_name))
                        {
                            self.error(format!(
                                "inherent method `{enum_name}.{variant_name}` requires an instance receiver"
                            ));
                            return error_expr();
                        }
                        self.error(format!(
                            "unknown associated member or variant `{variant_name}` on `{enum_name}`"
                        ));
                        return error_expr();
                    }
                    if self.collection.struct_layouts.contains_key(enum_name) {
                        if self
                            .collection
                            .inherent_members
                            .get(enum_name)
                            .is_some_and(|members| members.methods.contains_key(variant_name))
                        {
                            self.error(format!(
                                "inherent method `{enum_name}.{variant_name}` requires an instance receiver"
                            ));
                            return error_expr();
                        }
                        self.error(format!(
                            "unknown associated member `{variant_name}` on `{enum_name}`"
                        ));
                        return error_expr();
                    }
                }
            }
            return self.lower_bound_method_call(
                base,
                variant_name,
                &groups,
                Some(&actual_delimiters),
                BoundMethodConstraint::None,
                expected,
                context,
            );
        }
        self.error("calls require a named function, constructor, associated function, or method");
        error_expr()
    }

    fn call_delimiters_match(
        &mut self,
        name: &str,
        actual: &[GroupDelimiter],
        expected: &[GroupDelimiter],
    ) -> bool {
        for (index, actual) in actual.iter().enumerate() {
            let Some(expected) = expected.get(index).copied() else {
                self.error(format!(
                    "call to `{name}` supplies more argument groups than the declaration"
                ));
                return false;
            };
            if *actual != expected {
                self.error(format!(
                    "argument group {} in call to `{name}` uses `{}` but the parameter group uses `{}`",
                    index + 1,
                    actual.opening(),
                    expected.opening(),
                ));
                return false;
            }
        }
        true
    }

    fn named_compile_group_capacity(&self, name: &str) -> Option<usize> {
        self.collection
            .functions
            .get(name)
            .into_iter()
            .chain(self.collection.function_templates.get(name))
            .map(|function| {
                function
                    .compile_groups
                    .len()
                    .max(function.effects.compile_group_delimiters.len())
            })
            .max()
    }

    pub(super) fn named_call_delimiters_match(
        &mut self,
        name: &str,
        groups: &[&[CallArg]],
        actual: &[GroupDelimiter],
        context: &LowerCtx,
    ) -> bool {
        if !self.explicit_compile_delimiters_match(name, groups, actual, context) {
            return false;
        }
        let expected = self
            .collection
            .functions
            .get(name)
            .or_else(|| self.collection.function_templates.get(name))
            .map(|function| {
                let group_count = self
                    .lowering
                    .signatures
                    .get(name)
                    .map_or(function.groups.len(), |signature| signature.groups.len())
                    .max(usize::from(name.starts_with("$trait$impl$")));
                normalized_group_delimiters(
                    &function.effects.group_delimiters,
                    group_count,
                )
            });
        let explicit = actual
            .iter()
            .take_while(|delimiter| **delimiter == GroupDelimiter::Angle)
            .count()
            .min(self.named_compile_group_capacity(name).unwrap_or(0));
        expected.is_none_or(|expected| {
            self.call_delimiters_match(name, &actual[explicit..], &expected)
        })
    }

    pub(super) fn method_call_delimiters_match(
        &mut self,
        name: &str,
        _groups: &[&[CallArg]],
        actual: &[GroupDelimiter],
        _context: &LowerCtx,
    ) -> bool {
        let Some(function) = self
            .collection
            .functions
            .get(name)
            .or_else(|| self.collection.function_templates.get(name))
        else {
            return true;
        };
        let group_count = self
            .lowering
            .signatures
            .get(name)
            .map_or(function.groups.len(), |signature| signature.groups.len())
            .max(usize::from(name.starts_with("$trait$impl$")));
        let mut runtime = normalized_group_delimiters(&function.effects.group_delimiters, group_count)
            .into_iter()
            .skip(1)
            .collect::<Vec<_>>();
        if runtime.is_empty() {
            runtime.push(GroupDelimiter::Parenthesis);
        }
        let compile_capacity = self.named_compile_group_capacity(name).unwrap_or(0);
        let leading_angle_groups = actual
            .iter()
            .take_while(|delimiter| **delimiter == GroupDelimiter::Angle)
            .count();
        if compile_capacity > 0 && leading_angle_groups > compile_capacity {
            self.error(format!(
                "call to `{name}` supplies {leading_angle_groups} compile-time argument groups, but the declaration has {compile_capacity}",
            ));
            return false;
        }
        let explicit = leading_angle_groups.min(compile_capacity);
        for (index, delimiter) in actual.iter().take(explicit).enumerate() {
            if *delimiter != GroupDelimiter::Angle {
                self.error(format!(
                    "argument group {} in call to `{name}` uses `{}` but the parameter group uses `<`",
                    index + 1,
                    delimiter.opening(),
                ));
                return false;
            }
        }
        self.call_delimiters_match(name, &actual[explicit..], &runtime)
    }

    fn explicit_compile_delimiters_match(
        &mut self,
        name: &str,
        _groups: &[&[CallArg]],
        actual: &[GroupDelimiter],
        _context: &LowerCtx,
    ) -> bool {
        let Some(compile_capacity) = self.named_compile_group_capacity(name) else {
            return true;
        };
        let explicit = actual
            .iter()
            .take_while(|delimiter| **delimiter == GroupDelimiter::Angle)
            .count();
        if compile_capacity > 0 && explicit > compile_capacity {
            self.error(format!(
                "call to `{name}` supplies {explicit} compile-time argument groups, but the declaration has {}",
                compile_capacity
            ));
            return false;
        }
        true
    }

    fn lower_recursive_frame_call(
        &mut self,
        frame: &RecursiveFrameCall,
        groups: &[&[CallArg]],
        context: &mut LowerCtx,
    ) -> HirExpr {
        if groups.len() != 1 || groups[0].len() != frame.parameters.len() {
            self.error(format!(
                "recursive continuation frame expects {} argument(s), found {}",
                frame.parameters.len(),
                groups.first().map_or(0, |group| group.len())
            ));
            return error_expr();
        }
        let mut lowered = Vec::new();
        let mut loans = Vec::new();
        let mut temporaries = Vec::new();
        for capture in &frame.captures {
            lowered.push(self.lower_call_argument(
                &Expr::Name(capture.name.clone()),
                capture,
                context,
                &mut loans,
                &mut temporaries,
            ));
        }
        for (argument, parameter) in groups[0].iter().zip(&frame.parameters) {
            lowered.push(self.lower_call_argument(
                &argument.value,
                parameter,
                context,
                &mut loans,
                &mut temporaries,
            ));
        }
        self.release_loans(&loans, context);
        let call = HirExpr {
            ty: frame.result.clone(),
            kind: HirExprKind::Call {
                function: frame.function.clone(),
                arguments: lowered.clone(),
                consumed_callable: None,
                diverges: self.is_uninhabited_type(&frame.result),
            },
        };
        self.wrap_call_argument_temporaries(call, &mut lowered, temporaries, context)
    }

    pub(super) fn resolve_function_overload(
        &mut self,
        name: &str,
        groups: &[&[CallArg]],
    ) -> Option<String> {
        let candidates = self.collection.function_overloads.get(name)?.clone();
        let display_name = self.diagnostic_function_name(name);
        if !groups
            .iter()
            .flat_map(|group| group.iter())
            .any(|argument| argument.label.is_some())
        {
            self.error(format!(
                "overloaded call `{display_name}` requires named arguments to select an overload"
            ));
            return None;
        }
        let matches = self.matching_function_overloads(&candidates, groups, 0);
        match matches.as_slice() {
            [selected] => Some(selected.clone()),
            [] => {
                let supplied = groups
                    .iter()
                    .map(|group| {
                        format!(
                            "({})",
                            group
                                .iter()
                                .filter_map(|argument| argument.label.as_deref())
                                .collect::<Vec<_>>()
                                .join(", ")
                        )
                    })
                    .collect::<Vec<_>>()
                    .join("");
                self.error(format!(
                    "no overload of `{display_name}` matches named parameter groups {supplied}"
                ));
                None
            }
            _ => {
                self.error(format!(
                    "overloaded call `{display_name}` remains ambiguous; supply a parameter group whose names distinguish one overload"
                ));
                None
            }
        }
    }

    pub(super) fn resolve_inherent_overload(
        &mut self,
        target: &str,
        member: &str,
        is_method: bool,
        groups: &[&[CallArg]],
    ) -> Option<String> {
        let key = (target.to_owned(), member.to_owned(), is_method);
        let candidates = self.collection.inherent_overloads.get(&key)?.clone();
        let target = self.diagnostic_type_name(&Ty::Struct(target.to_owned()));
        if !groups
            .iter()
            .flat_map(|group| group.iter())
            .any(|argument| argument.label.is_some())
        {
            self.error(format!(
                "overloaded call `{target}.{member}` requires named arguments to select an overload"
            ));
            return None;
        }
        let matches = self.matching_function_overloads(&candidates, groups, usize::from(is_method));
        match matches.as_slice() {
            [selected] => Some(selected.clone()),
            [] => {
                self.error(format!(
                    "no overload of `{target}.{member}` matches the supplied named parameter groups"
                ));
                None
            }
            _ => {
                self.error(format!(
                    "overloaded call `{target}.{member}` remains ambiguous; name a parameter from a distinguishing group"
                ));
                None
            }
        }
    }

    pub(super) fn matching_function_overloads(
        &self,
        candidates: &[String],
        groups: &[&[CallArg]],
        parameter_group_offset: usize,
    ) -> Vec<String> {
        candidates
            .iter()
            .filter(|candidate| {
                let parameter_names = if let Some(signature) =
                    self.lowering.signatures.get(*candidate)
                {
                    signature.groups[parameter_group_offset..]
                        .iter()
                        .map(|group| {
                            group
                                .iter()
                                .map(|parameter| parameter.name.clone())
                                .collect::<Vec<_>>()
                        })
                        .collect::<Vec<_>>()
                } else if let Some(template) = self.collection.function_templates.get(*candidate) {
                    template.groups[parameter_group_offset..]
                        .iter()
                        .map(|group| {
                            group
                                .iter()
                                .map(|parameter| parameter.name.clone())
                                .collect::<Vec<_>>()
                        })
                        .collect::<Vec<_>>()
                } else {
                    return false;
                };
                let matches_runtime = |runtime_groups: &[&[CallArg]]| {
                    if runtime_groups.len() > parameter_names.len() {
                        return false;
                    }
                    runtime_groups
                        .iter()
                        .zip(&parameter_names)
                        .all(|(arguments, parameters)| {
                            if arguments.len() != parameters.len() {
                                return false;
                            }
                            let labeled = arguments
                                .iter()
                                .filter(|argument| argument.label.is_some())
                                .count();
                            labeled == 0
                                || labeled == arguments.len()
                                    && parameters.iter().all(|parameter| {
                                        arguments
                                            .iter()
                                            .filter(|argument| {
                                                argument.label.as_deref()
                                                    == Some(parameter.as_str())
                                            })
                                            .count()
                                            == 1
                                    })
                        })
                };
                if self.lowering.signatures.contains_key(*candidate) {
                    matches_runtime(groups)
                } else {
                    let compile_group_count = self.collection.function_templates[*candidate]
                        .compile_groups
                        .len();
                    (0..=compile_group_count.min(groups.len())).any(|runtime_start| {
                        groups[runtime_start..]
                            .iter()
                            .flat_map(|group| group.iter())
                            .any(|argument| argument.label.is_some())
                            && matches_runtime(&groups[runtime_start..])
                    })
                }
            })
            .cloned()
            .collect()
    }

    pub(super) fn ordered_call_arguments<'a>(
        &mut self,
        owner: &str,
        group_number: usize,
        arguments: &'a [CallArg],
        parameter_names: &[String],
    ) -> Option<Vec<&'a CallArg>> {
        if arguments.iter().all(|argument| argument.label.is_none())
            && arguments.len() != parameter_names.len()
        {
            self.error(format!(
                "argument count mismatch in group {group_number} of `{owner}`: expected {}, found {}",
                parameter_names.len(),
                arguments.len()
            ));
            return None;
        }
        let mut ordered = vec![None; parameter_names.len()];
        let mut labeled = false;
        let mut last_labeled_index = None;
        for (source_index, argument) in arguments.iter().enumerate() {
            let Some(label) = argument.label.as_deref() else {
                if labeled || source_index >= ordered.len() {
                    self.error(format!(
                        "positional arguments in group {group_number} of `{owner}` must precede named arguments"
                    ));
                    return None;
                }
                ordered[source_index] = Some(argument);
                continue;
            };
            labeled = true;
            let Some(index) = parameter_names.iter().position(|name| name == label) else {
                self.error(format!(
                    "unknown parameter `{label}` in group {group_number} of `{owner}`"
                ));
                return None;
            };
            if ordered[index].is_some() {
                self.error(format!(
                    "duplicate argument for parameter `{label}` in group {group_number} of `{owner}`"
                ));
                return None;
            }
            if last_labeled_index.is_some_and(|previous| index < previous) {
                self.error(format!(
                    "named arguments in group {group_number} of `{owner}` must follow parameter declaration order"
                ));
                return None;
            }
            ordered[index] = Some(argument);
            last_labeled_index = Some(index);
        }
        for (index, argument) in ordered.iter().enumerate() {
            if argument.is_none() {
                self.error(format!(
                    "missing argument for parameter `{}` in group {group_number} of `{owner}`",
                    parameter_names[index]
                ));
                return None;
            }
        }
        Some(ordered.into_iter().flatten().collect())
    }
}

fn brace_body_value(expression: &Expr) -> Option<&Expr> {
    match expression.unlocated() {
        Expr::Closure(parameters, body) if parameters.is_empty() => Some(body),
        _ => None,
    }
}

fn is_empty_brace_body(expression: &Expr) -> bool {
    matches!(
        expression.unlocated(),
        Expr::Closure(parameters, body)
            if parameters.is_empty()
                && matches!(body.unlocated(), Expr::Block(statements, None) if statements.is_empty())
    )
}

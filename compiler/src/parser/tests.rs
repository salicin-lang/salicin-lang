use super::*;

#[test]
fn parses_brace_first_named_callables_and_bodyless_requirements() {
    let program = parse(
        "let add = {\n\
         (x: i32, y: i32): i32 =>\n\
         let sum: i32 = x + y\n\
         sum\n\
         }\n\
         let id = { <T: type>(value: T): T => value }\n\
         let protocol = trait {\n\
         required: (self)(value: i32): i32;\n\
         defaulted: (self)(): i32 = 42;\n\
         }\n",
    )
    .expect("brace-first declarations, requirements, and defaults must parse");
    let Item::Function(add) = &program.items[0] else {
        panic!("expected add function");
    };
    assert!(
        matches!(add.body, Some(Expr::Block(ref statements, Some(_))) if statements.len() == 1)
    );
    let Item::Function(id) = &program.items[1] else {
        panic!("expected id function");
    };
    assert_eq!(id.compile_groups[0][0].name, "T");
    let Item::Trait(protocol) = &program.items[2] else {
        panic!("expected trait");
    };
    let TraitMember::Function(required) = &protocol.members[0] else {
        panic!("expected required method");
    };
    assert!(required.body.is_none());
    let TraitMember::Function(defaulted) = &protocol.members[1] else {
        panic!("expected default method");
    };
    assert!(defaulted.body.is_some());
}

#[test]
fn parses_braced_builtin_foreign_effect_and_requirement_metadata() {
    let program = parse(
        "let intrinsic = { (value: i32): i32 => builtin() }\n\
         let c_abs = { (value: i32): i32 => foreign(c, \"abs\") }\n\
         let constrained = { <T: type>(value: T): T requires(T is Copy) => value }\n",
    )
    .expect("callable metadata must remain inside the outer braces");
    let Item::Function(intrinsic) = &program.items[0] else {
        panic!("expected builtin function");
    };
    assert!(intrinsic.builtin);
    let Item::Function(c_abs) = &program.items[1] else {
        panic!("expected foreign function");
    };
    assert_eq!(c_abs.foreign.as_ref().unwrap().link_name, "abs");
    assert!(c_abs.effects.unsafety);
    let Item::Function(constrained) = &program.items[2] else {
        panic!("expected constrained function");
    };
    assert_eq!(constrained.where_predicates.len(), 1);
}

#[test]
fn parses_only_braced_anonymous_and_pattern_callables() {
    let program = parse(
        "let main = { (): i32 =>\n\
         let increment = { (x: i32): i32 => x + 1 }\n\
         increment(1)\n\
         }\n\
         let select: (Option<i32>): i32 = { Some(x) if x > 0 => x, None => 0, Some(_) => -1 }\n",
    )
    .expect("anonymous callable forms must parse inside braces");
    let Item::Function(main) = &program.items[0] else {
        panic!("expected main function");
    };
    let Some(Expr::Block(statements, Some(_))) = &main.body else {
        panic!("expected main body");
    };
    let [Stmt::Let(increment)] = statements.as_slice() else {
        panic!("expected local closure binding");
    };
    assert!(
        matches!(increment.value, Expr::Closure(ref parameters, ref body)
        if parameters.len() == 1 && matches!(body.as_ref(), Expr::Block(_, Some(_))))
    );
    let Item::Function(select) = &program.items[1] else {
        panic!("expected named pattern callable");
    };
    assert!(select.groups[0][0]
        .name
        .starts_with("$match$callable$input$"));
    assert!(matches!(select.body, Some(Expr::Match { ref arms, .. }) if arms.len() == 3));

    let error = parse("let main = { (): i32 => let old = (x) => x\n0 }\n")
        .expect_err("bare callable literals are removed");
    assert!(error.message.contains("outer braces"), "{error:?}");
}

#[test]
fn promotes_boolean_pattern_callables_and_preserves_ordinary_closure_globals() {
    let program = parse(
        "let select = { true => 1, false => 0 }\n\
         let value = { 42 }\n",
    )
    .expect("an unambiguous pattern callable must become a named function");

    let Item::Function(select) = &program.items[0] else {
        panic!("expected named pattern callable");
    };
    assert_eq!(select.groups.len(), 1);
    assert_eq!(select.groups[0].len(), 1);
    assert_eq!(select.groups[0][0].ty, Type::Bool);
    assert!(select.return_type.is_none());
    assert!(matches!(select.body, Some(Expr::Match { ref arms, .. }) if arms.len() == 2));
    assert!(matches!(program.items[1], Item::Global(_)));
}

#[test]
fn requires_annotations_for_ambiguous_top_level_pattern_callables() {
    let error = parse("let select = { Some(x) => x, None => 0 }\n")
        .expect_err("constructor patterns cannot determine their generic input type");
    assert!(
        error
            .message
            .contains("requires a callable type annotation"),
        "{error:?}"
    );
}

#[test]
fn brace_calls_treat_pattern_arms_as_one_callable_argument() {
    let program = parse("let use = { (): i32 => choose { Some(x) => x, None => 0 } }\n")
        .expect("a pattern callable must remain one brace-call argument");
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Expr::DelimitedCall { arguments, .. } = function_tail(function) else {
        panic!("expected brace call");
    };
    assert!(matches!(arguments.as_slice(), [CallArg {
        label: None,
        value: Expr::Closure(parameters, body),
    }] if parameters.len() == 1 && matches!(body.as_ref(), Expr::Match { arms, .. } if arms.len() == 2)));
}

#[test]
fn parses_callable_effect_operations_and_normalizes_handler_clauses() {
    let program = parse(
        "let state_effect = <S: type> effect {\n\
         get: (): S\n\
         put: (value: S): ()\n\
         }\n\
         let run = { (state: i32): i32 =>\n\
         state_effect<i32>.handle(state_effect<i32>.get()) {\n\
         get(resume) => resume(state),\n\
         put(value, resume) => do { resume(value) },\n\
         Return(value) => value,\n\
         }\n\
         }\n",
    )
    .expect("callable effect operations and handler clauses must parse");
    let Item::Effect(effect) = &program.items[0] else {
        panic!("expected effect");
    };
    assert_eq!(
        effect
            .operations
            .iter()
            .map(|operation| operation.name.as_str())
            .collect::<Vec<_>>(),
        ["get", "put"]
    );
    let Item::Function(run) = &program.items[1] else {
        panic!("expected run function");
    };
    let Expr::DelimitedCall {
        delimiter,
        arguments,
        ..
    } = function_tail(run)
    else {
        panic!("expected canonical handler brace call");
    };
    assert_eq!(*delimiter, GroupDelimiter::Brace);
    assert_eq!(
        arguments
            .iter()
            .map(|argument| argument.label.as_deref())
            .collect::<Vec<_>>(),
        [Some("get"), Some("put"), Some("done"), Some("action")]
    );
    assert!(matches!(arguments.last(), Some(CallArg {
        value: Expr::Closure(parameters, _), ..
    }) if parameters.is_empty()));
}

#[test]
fn rejects_removed_callable_effect_and_handler_forms() {
    for source in [
        "let old = (x: i32): i32 => x\n",
        "let old = effect { let Get = (): i32 }\n",
        "let old = { (): i32 => State.handle { action: { 0 } } }\n",
    ] {
        assert!(parse(source).is_err(), "removed syntax parsed: {source}");
    }
    let handler = parse("let old = { (): i32 => State.handle { action: { 0 } } }\n")
        .expect_err("old labeled handlers are removed");
    assert!(handler.message.contains("old labeled handler syntax"));
}

#[test]
fn parses_prefix_effect_callable_declarations_and_types() {
    let program = parse(
        "let apply = { <e: effects>with<e>\n\
             (action: with<e>(i32): i32)\n\
             (value: i32): i32 => action(value) }\n\
             let pure = { (value: i32): i32 =>  value }\n",
    )
    .expect("prefix effect callable syntax must parse");
    let Item::Function(apply) = &program.items[0] else {
        panic!("expected apply function");
    };
    assert_eq!(apply.effects.parameters, ["e"]);
    let Type::Function { effects, .. } = &apply.groups[0][0].ty else {
        panic!("expected callable action parameter");
    };
    assert_eq!(effects.parameters, ["e"]);
    let Item::Function(pure) = &program.items[1] else {
        panic!("expected pure function");
    };
    assert_eq!(pure.effects, FunctionEffects::default());
}

#[test]
fn prefix_with_requires_a_callable_operand_and_accepts_an_empty_row() {
    let program = parse("let use = { (action: with<>(i32): i32): i32 =>  action(1) }\n")
        .expect("an empty effect row is a pure callable");
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Type::Function { effects, .. } = &function.groups[0][0].ty else {
        panic!("expected callable parameter");
    };
    assert_eq!(effects, &FunctionEffects::default());

    let error = parse("let value = with<io>(i32) 1\n")
        .expect_err("with must reject a non-callable operand");
    assert!(
        error.message.contains("accepts only a callable type")
            || error.message.contains("parameter name")
            || error
                .message
                .contains("effect annotations require a function declaration"),
        "{}",
        error.message
    );
}

#[test]
fn parses_contextual_test_declarations_as_private_unit_throwing_functions() {
    let program =
        parse("test(\"arithmetic works\") {\n  core.error.throw(\"broken\")\n}\n").unwrap();
    let [Item::Function(test)] = program.items.as_slice() else {
        panic!("expected one test function");
    };
    assert_eq!(test.name, "$test$61726974686d6574696320776f726b73");
    assert_eq!(test.groups, vec![Vec::new()]);
    assert_eq!(
        test.effects.group_delimiters,
        vec![GroupDelimiter::Parenthesis]
    );
    assert_eq!(test.return_type, Some(Type::Unit));
    assert_eq!(
        test.effects,
        FunctionEffects {
            custom: vec![Type::Named(
                "core.error.throwing".to_owned(),
                vec![Type::Named("core.string.String".to_owned(), Vec::new())],
            )],
            ..FunctionEffects::default()
        }
    );
    assert_eq!(program.item_visibilities, [Visibility::Private]);

    let visible = parse("pub test(\"arithmetic\") { () }\n")
        .expect_err("test declarations must remain runner-private");
    assert!(visible.message.contains("cannot have visibility"));
    let identifier = parse("test arithmetic { true }\n")
        .expect_err("test registration names must be string literals");
    assert!(identifier.message.contains("`(` after `test`"));
    let empty = parse("test(\"\") { () }\n").expect_err("test names must be useful");
    assert!(empty.message.contains("cannot be empty"));
}

fn function_tail(function: &Function) -> &Expr {
    let Some(Expr::Block(_, Some(tail))) = &function.body else {
        panic!("expected function body block with a tail value");
    };
    tail.unlocated()
}

fn flatten_test_call<'a>(
    expression: &'a Expr,
    groups: &mut Vec<(GroupDelimiter, &'a [CallArg])>,
) -> &'a Expr {
    let expression = expression.unlocated();
    match expression {
        Expr::Call(callee, arguments) => {
            let root = flatten_test_call(callee, groups);
            groups.push((GroupDelimiter::Parenthesis, arguments));
            root
        }
        Expr::DelimitedCall {
            callee,
            delimiter,
            arguments,
        } => {
            let root = flatten_test_call(callee, groups);
            groups.push((*delimiter, arguments));
            root
        }
        _ => expression,
    }
}

fn match_call_parts(expression: &Expr) -> (&Expr, &[MatchArm]) {
    let Expr::Match { scrutinee, arms } = expression.unlocated() else {
        panic!("expected a match expression");
    };
    (scrutinee, arms)
}

fn if_call_parts(expression: &Expr) -> (&Expr, &Expr, &Expr) {
    fn branch(group: &[CallArg]) -> &Expr {
        let [CallArg {
            label: None,
            value: Expr::Closure(parameters, body),
        }] = group
        else {
            panic!("expected one unlabeled if branch closure");
        };
        assert!(parameters.is_empty());
        body.as_ref()
    }

    let mut groups = Vec::new();
    assert_eq!(
        flatten_test_call(expression, &mut groups),
        &Parser::core_if_function()
    );
    let [(GroupDelimiter::Parenthesis, condition_group), (GroupDelimiter::Brace, then_group), (GroupDelimiter::Brace, else_group)] =
        groups.as_slice()
    else {
        panic!("expected condition, then, and else groups");
    };
    let [CallArg {
        label: None,
        value: condition,
    }] = *condition_group
    else {
        panic!("expected one unlabeled if condition");
    };
    (condition, branch(then_group), branch(else_group))
}

#[test]
fn parses_globals_and_curried_functions() {
    let program = parse(
        "let answer: i32 = 40 + 2\n\
             let add = { (copy x: i32)(y: i32): i32 =>  x + y }\n",
    )
    .unwrap();
    assert_eq!(program.items.len(), 2);
    let Item::Function(function) = &program.items[1] else {
        panic!("expected function");
    };
    assert_eq!(function.name, "add");
    assert_eq!(function.groups.len(), 2);
    assert_eq!(function.groups[0][0].mode, PassMode::Copy);
    assert_eq!(function.return_type, Some(Type::I32));
}

#[test]
fn rejects_name_side_declaration_signature_groups() {
    for source in [
        "let identity<T: type>(value: T): T { value }\n",
        "let identity(value: i32): i32 { value }\n",
        "let value = struct {}\nextend(value) { let read(self: Borrow<self>)(): i32 => { 0 } }\n",
    ] {
        let error = parse(source).unwrap_err();
        assert!(
            error
                .message
                .contains("declaration signature groups must follow `=`"),
            "{}",
            error.message
        );
    }

    let error = parse("let protocol = trait { read(self: Borrow<self>)(): i32 }\n").unwrap_err();
    assert!(error
        .message
        .contains("expected a trait member declaration"));
}

#[test]
fn rhs_callable_bodies_follow_fat_arrows() {
    let program = parse(
        "let typed = { (p: i32) => \n  let value: i32 = p\n  value\n}\n\
         let controlled = { (condition: bool) => \n  if(condition) { 1 } else: { 0 }\n}\n\
         let braced = { {p: i32}{q: i32} => \n  let value: i32 = p\n  value + q\n}\n\
         let empty = { {} => }\n\
         let nonempty = { {} =>  42 }\n\
         let squared = { [p: i32] =>  p }\n\
             let bodyless = { {value: i32}: i32 }\n",
    )
    .expect("fat arrows separate callable signatures from their bodies");

    for (index, group_count) in [(0, 1), (1, 1), (2, 2), (3, 1), (4, 1), (5, 1), (6, 1)] {
        let Item::Function(function) = &program.items[index] else {
            panic!("expected RHS callable declaration");
        };
        assert_eq!(function.groups.len(), group_count);
        assert_eq!(function.body.is_some(), index != 6);
    }
    let Item::Function(braced) = &program.items[2] else {
        unreachable!();
    };
    assert_eq!(
        braced.effects.group_delimiters,
        [GroupDelimiter::Brace, GroupDelimiter::Brace]
    );
    let Item::Function(empty) = &program.items[3] else {
        unreachable!();
    };
    let Item::Function(nonempty) = &program.items[4] else {
        unreachable!();
    };
    assert!(empty.groups[0].is_empty());
    assert!(nonempty.groups[0].is_empty());
    let Item::Function(squared) = &program.items[5] else {
        unreachable!();
    };
    assert_eq!(squared.effects.group_delimiters, [GroupDelimiter::Square]);
    let Item::Function(bodyless) = &program.items[6] else {
        unreachable!();
    };
    assert!(bodyless.body.is_none());
}

#[test]
fn parses_anonymous_callable_body_with_a_typed_local() {
    let program = parse(
        "let invoke = { (action: (i32): i32): i32 =>  action(42) }\n\
         let main = { (): i32 => \n\
           let action = { (p: i32) =>  let value: i32 = p; value }\n\
           invoke(action)\n\
         }\n",
    )
    .expect("typed locals do not turn anonymous callable bodies into parameter groups");
    let Item::Function(main) = &program.items[1] else {
        panic!("expected main function");
    };
    assert!(main.body.is_some());
}

#[test]
fn grouped_global_expressions_remain_values() {
    let program = parse(
        "let grouped: i32 = (42)\n\
         let closure = { let value: i32 = 42; value }\n",
    )
    .expect("ordinary grouped global expressions must remain values");
    assert!(matches!(
        program.items.as_slice(),
        [Item::Global(_), Item::Global(_)]
    ));
}

#[test]
fn parses_function_effects_and_rejects_them_on_values() {
    let program =
        parse("let read = { with<unsafety>(pointer: Ptr<i32>): i32 =>  *pointer }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(!function.effects.unsafety);
    assert_eq!(
        function.effects.custom,
        vec![Type::Named("unsafety".to_owned(), Vec::new())]
    );

    let program = parse(
        "let answer = { (unsafe: i32): i32 =>  unsafe }\n\
             let recover = { (try: i32): i32 =>  try }\n",
    )
    .unwrap();
    assert_eq!(program.items.len(), 2);

    let error = parse("let f = (): i32 ! unsafe { 42 }\n").unwrap_err();
    assert!(!error.message.is_empty());

    let program =
        parse("let fallible = { with<throwing<bool>, unsafety>(): i32 =>  throw(true) }\n")
            .unwrap();
    let Item::Function(fallible) = &program.items[0] else {
        panic!("expected fallible function");
    };
    assert_eq!(fallible.return_type, Some(Type::I32));
    assert!(!fallible.effects.unsafety);
    assert_eq!(fallible.effects.failure, None);
    assert_eq!(
        fallible.effects.custom,
        vec![
            Type::Named("throwing".to_owned(), vec![Type::Bool]),
            Type::Named("unsafety".to_owned(), Vec::new())
        ]
    );

    for source in [
        "let f = { with<unsafety, unsafety>(): i32 =>  0 }\n",
        "let f = { with<throwing<bool>, throwing<bool>>(): i32 =>  0 }\n",
    ] {
        let error = parse(source).unwrap_err();
        assert!(error.message.contains("duplicate"));
    }

    let contextual =
        parse("let f = { with<unsafe, try<bool>>(): i32 =>  0 }\n").expect("custom effect names");
    let Item::Function(contextual) = &contextual.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        contextual.effects.custom,
        [
            Type::Named("unsafe".to_owned(), Vec::new()),
            Type::Named("try".to_owned(), vec![Type::Bool]),
        ]
    );
}

#[test]
fn bitwise_and_shift_precedence_is_fixed_by_the_language() {
    let program = parse(
        "let shifts = 1 + 2 << 3 + 4\n\
             let bits = 1 | 2 ^ 3 & 4\n",
    )
    .unwrap();

    let Item::Global(shifts) = &program.items[0] else {
        panic!("expected shifts global");
    };
    assert!(matches!(
        &shifts.value,
        Expr::Binary(left, BinaryOp::Shl, right)
            if matches!(left.as_ref(), Expr::Binary(_, BinaryOp::Add, _))
                && matches!(right.as_ref(), Expr::Binary(_, BinaryOp::Add, _))
    ));

    let Item::Global(bits) = &program.items[1] else {
        panic!("expected bits global");
    };
    assert!(matches!(
        &bits.value,
        Expr::Binary(one, BinaryOp::BitOr, xor)
            if matches!(one.as_ref(), Expr::Integer(1))
                && matches!(
                    xor.as_ref(),
                    Expr::Binary(two, BinaryOp::BitXor, and)
                        if matches!(two.as_ref(), Expr::Integer(2))
                            && matches!(and.as_ref(), Expr::Binary(_, BinaryOp::BitAnd, _))
                )
    ));
}

#[test]
fn preserves_top_level_visibility_alongside_items() {
    let program = parse(
        "let private = 0\n\
             pub let exported = 1\n\
             pub(package) let shared = 2\n",
    )
    .unwrap();

    assert_eq!(
        program.item_visibilities,
        vec![Visibility::Private, Visibility::Public, Visibility::Package,]
    );
    assert_eq!(program.items.len(), program.item_visibilities.len());
    assert!(program.uses.is_empty());
}

#[test]
fn parses_and_expands_import_declarations() {
    let mut program = parse(
        "use net.http.client\n\
             use net.http.client as other_client\n\
             pub use net.http.{get, post as send}\n\
             pub(package) use root.core.value\n\
             let answer = 42\n",
    )
    .unwrap();
    assert!(program
        .uses
        .iter()
        .all(|declaration| declaration.source.is_some()));
    for declaration in &mut program.uses {
        declaration.source = None;
    }

    assert_eq!(
        program.uses,
        vec![
            UseDecl {
                visibility: Visibility::Private,
                path: vec!["net".into(), "http".into(), "client".into()],
                alias: None,
                source: None,
            },
            UseDecl {
                visibility: Visibility::Private,
                path: vec!["net".into(), "http".into(), "client".into()],
                alias: Some("other_client".into()),
                source: None,
            },
            UseDecl {
                visibility: Visibility::Public,
                path: vec!["net".into(), "http".into(), "get".into()],
                alias: None,
                source: None,
            },
            UseDecl {
                visibility: Visibility::Public,
                path: vec!["net".into(), "http".into(), "post".into()],
                alias: Some("send".into()),
                source: None,
            },
            UseDecl {
                visibility: Visibility::Package,
                path: vec!["root".into(), "core".into(), "value".into()],
                alias: None,
                source: None,
            },
        ]
    );
    assert_eq!(program.items.len(), 1);
    assert_eq!(program.item_visibilities, vec![Visibility::Private]);
}

#[test]
fn parses_qualified_let_bindings_as_transparent_entity_aliases() {
    let mut program = parse(
        "let Option = core.Option\n\
             let http_client = net.http.client\n\
             pub let status = net.http.status\n\
             pub(package) let value = root.core.value\n\
             let snapshot: value = (object.member)\n\
             let answer = 42\n",
    )
    .unwrap();
    assert!(program
        .uses
        .iter()
        .all(|declaration| declaration.source.is_some()));
    for declaration in &mut program.uses {
        declaration.source = None;
    }

    assert_eq!(
        program.uses,
        vec![
            UseDecl {
                visibility: Visibility::Private,
                path: vec!["core".into(), "Option".into()],
                alias: Some("Option".into()),
                source: None,
            },
            UseDecl {
                visibility: Visibility::Private,
                path: vec!["net".into(), "http".into(), "client".into()],
                alias: Some("http_client".into()),
                source: None,
            },
            UseDecl {
                visibility: Visibility::Public,
                path: vec!["net".into(), "http".into(), "status".into()],
                alias: Some("status".into()),
                source: None,
            },
            UseDecl {
                visibility: Visibility::Package,
                path: vec!["root".into(), "core".into(), "value".into()],
                alias: Some("value".into()),
                source: None,
            },
        ]
    );
    assert_eq!(program.items.len(), 2);
    assert_eq!(
        program.item_visibilities,
        vec![Visibility::Private, Visibility::Private]
    );
    assert!(matches!(
        &program.items[0],
        Item::Global(Binding {
            name,
            value: Expr::Member(_, member),
            ..
        }) if name == "snapshot" && member == "member"
    ));
}

#[test]
fn rejects_empty_duplicate_and_invalid_imports() {
    let empty = parse("use net.http.{}\n").unwrap_err();
    assert!(empty.message.contains("cannot be empty"));

    let duplicate = parse("use net.http.{get, post as get}\n").unwrap_err();
    assert!(duplicate.message.contains("duplicate import binding `get`"));

    let duplicates_for_semantic_resolution = parse("use net.http.get\nuse other.get\n").unwrap();
    assert_eq!(duplicates_for_semantic_resolution.uses.len(), 2);

    for alias in ["self", "_"] {
        let error = parse(&format!("use net.http.client as {alias}\n")).unwrap_err();
        assert!(error.message.contains("cannot be used as an import alias"));
    }

    let missing_alias = parse("use net.http.client as\n").unwrap_err();
    assert!(missing_alias.message.contains("import alias"));

    for source in ["use root\n", "use super.super\n", "use self\n"] {
        let error = parse(source).unwrap_err();
        assert!(error.message.contains("explicit usable alias"), "{error:?}");
    }
    for source in [
        "use root.super.value\n",
        "use super.root.value\n",
        "use net.{root}\n",
        "use net.{super}\n",
        "use net.{_}\n",
    ] {
        let error = parse(source).unwrap_err();
        assert!(
            error.message.contains("path segment"),
            "{source}: {error:?}"
        );
    }

    let anchors = parse(
        "use root as package_root\n\
             use super.super as ancestor\n\
             use self as current\n",
    )
    .unwrap();
    assert_eq!(
        anchors
            .uses
            .iter()
            .map(|import| import.alias.as_deref())
            .collect::<Vec<_>>(),
        vec![Some("package_root"), Some("ancestor"), Some("current")]
    );

    let contextual = parse("use net.{self as contextual}\nuse root.self.value\n").unwrap();
    assert_eq!(contextual.uses[0].alias.as_deref(), Some("contextual"));
    let implicit_self = parse("use net.{self}\n").unwrap_err();
    assert!(implicit_self.message.contains("explicit usable alias"));
}

#[test]
fn rejects_misplaced_ordinary_path_anchors() {
    for source in [
        "let bad = { (): foo.root.value =>  0 }\n",
        "let bad = { (): i32 =>  root.super.value }\n",
        "let bad = { (value: root.option): i32 =>  match(value) { root.super.Option.None => 0 } }\n",
    ] {
        let error = parse(source).unwrap_err();
        assert!(error.message.contains("first path segment"), "{error:?}");
    }

    parse(
        "let ok = { (value: super.super.model.value): i32 =>  super.super.api.read(root.self.value) }\n",
    )
    .unwrap();
}

#[test]
fn accepts_root_super_and_contextual_self_in_ordinary_paths() {
    let program = parse(
            "let resolve = { (value: root.model.value): super.model.result =>  root.api.call(super.value) }\n\
             let unwrap = { (value: root.option): i32 =>  match(value) { root.option.Some(self) => self } }\n",
        )
        .unwrap();

    let Item::Function(resolve) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        resolve.groups[0][0].ty,
        Type::Named("root.model.value".into(), Vec::new())
    );
    assert_eq!(
        resolve.return_type,
        Some(Type::Named("super.model.result".into(), Vec::new()))
    );
    assert!(matches!(
        function_tail(resolve),
        Expr::Call(callee, arguments)
            if matches!(callee.as_ref(), Expr::Member(base, name)
                if name == "call"
                    && matches!(base.as_ref(), Expr::Member(root, name)
                        if name == "api" && root.as_ref() == &Expr::Name("root".into())))
                && matches!(&arguments[0].value, Expr::Member(base, name)
                    if name == "value" && base.as_ref() == &Expr::Name("super".into()))
    ));

    let Item::Function(unwrap) = &program.items[1] else {
        panic!("expected function");
    };
    let (_, cases) = match_call_parts(function_tail(unwrap));
    assert!(matches!(
        &cases[0].pattern,
        Pattern::Constructor { path, fields: PatternFields::Positional(fields) }
            if path == &vec!["root".to_owned(), "option".to_owned(), "Some".to_owned()]
                && fields == &vec![Pattern::Binding("self".into())]
    ));
}

#[test]
fn parses_dotted_type_paths() {
    let program = parse(
        "let convert = { (value: net.http.point): net.http.result<core.status> =>  value }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };

    assert_eq!(
        function.groups[0][0].ty,
        Type::Named("net.http.point".into(), Vec::new())
    );
    assert_eq!(
        function.return_type,
        Some(Type::Named(
            "net.http.result".into(),
            vec![Type::Named("core.status".into(), Vec::new())],
        ))
    );
}

#[test]
fn rejects_visibility_where_it_is_not_supported_yet() {
    let extension = parse("pub extend(thing) {}\n").unwrap_err();
    assert!(extension
        .message
        .contains("`extend` declarations cannot have visibility"));

    let trait_member = parse("let protocol = trait { pub f: (value: i32): i32 }\n").unwrap_err();
    assert!(trait_member.message.contains("visibility on trait members"));

    let extend_member = parse("extend(thing) { pub(package) let answer = 42 }\n").unwrap_err();
    assert!(extend_member.message.contains("extend members"));
}

#[test]
fn separates_compile_time_and_runtime_parameter_groups() {
    let program = parse(
        "let identity = { <t: type>(value: t): t =>  value }\n\
             let staged = { <t: type><u: type>(value: t): u =>  value }\n",
    )
    .unwrap();

    let Item::Function(identity) = &program.items[0] else {
        panic!("expected generic function");
    };
    assert_eq!(identity.compile_groups.len(), 1);
    assert_eq!(identity.compile_groups[0].len(), 1);
    assert_eq!(identity.compile_groups[0][0].name, "t");
    assert_eq!(identity.compile_groups[0][0].kind, Sort::Type);
    assert_eq!(identity.groups.len(), 1);
    assert_eq!(
        identity.groups[0][0].ty,
        Type::Named("t".into(), Vec::new())
    );
    assert_eq!(
        identity.return_type,
        Some(Type::Named("t".into(), Vec::new()))
    );

    let Item::Function(staged) = &program.items[1] else {
        panic!("expected generic function");
    };
    assert_eq!(
        staged
            .compile_groups
            .iter()
            .map(Vec::len)
            .collect::<Vec<_>>(),
        vec![1, 1]
    );
    assert_eq!(staged.groups.len(), 1);
}

#[test]
fn void_is_not_a_unit_type_alias() {
    let program = parse("let invalid = { (value: void): () =>  () }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        function.groups[0][0].ty,
        Type::Named("void".to_owned(), Vec::new())
    );
}

#[test]
fn preserves_all_function_group_delimiters_and_tight_calls() {
    let program = parse(
        "let combine = { <t: type>[left: t]{right: t}(last: t): t =>  last }\n\
             let value = combine<i32>[1]{2}(3)\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        function.effects.compile_group_delimiters,
        [GroupDelimiter::Angle]
    );
    assert_eq!(
        function.effects.group_delimiters,
        [
            GroupDelimiter::Square,
            GroupDelimiter::Brace,
            GroupDelimiter::Parenthesis,
        ]
    );
    let Item::Global(binding) = &program.items[1] else {
        panic!("expected global");
    };
    let mut groups = Vec::new();
    let root = flatten_test_call(&binding.value, &mut groups);
    assert!(matches!(root, Expr::Index { base, .. }
        if matches!(base.as_ref(), Expr::DelimitedCall {
            callee,
            delimiter: GroupDelimiter::Angle,
            ..
        } if callee.as_ref() == &Expr::Name("combine".into()))));
    assert_eq!(
        groups
            .iter()
            .map(|(delimiter, _)| *delimiter)
            .collect::<Vec<_>>(),
        [GroupDelimiter::Brace, GroupDelimiter::Parenthesis]
    );
    let Expr::Call(paren, _) = &binding.value else {
        panic!("expected parenthesis call");
    };
    let Expr::DelimitedCall {
        callee: brace,
        delimiter: GroupDelimiter::Brace,
        ..
    } = paren.as_ref()
    else {
        panic!("expected brace call");
    };
    assert!(matches!(brace.as_ref(), Expr::Index { base, .. }
    if matches!(base.as_ref(), Expr::DelimitedCall {
        delimiter: GroupDelimiter::Angle,
        ..
    })));
}

#[test]
fn distinguishes_tight_angle_calls_from_spaced_comparisons() {
    let program = parse("let less = 1 < 2\nlet call = select<1>\n").unwrap();
    assert!(matches!(
        &program.items[0],
        Item::Global(Binding {
            value: Expr::Binary(_, BinaryOp::Lt, _),
            ..
        })
    ));
    assert!(matches!(
        &program.items[1],
        Item::Global(Binding {
            value: Expr::DelimitedCall {
                delimiter: GroupDelimiter::Angle,
                ..
            },
            ..
        })
    ));
    assert!(parse("let invalid = 1>0\n")
        .unwrap_err()
        .message
        .contains("require whitespace"));
}

#[test]
fn splits_nested_angle_closers_from_shift_tokens() {
    let program = parse("let value = outer<inner<1>>\n").unwrap();
    let Item::Global(binding) = &program.items[0] else {
        panic!("expected global");
    };
    assert!(matches!(
        &binding.value,
        Expr::DelimitedCall {
            delimiter: GroupDelimiter::Angle,
            arguments,
            ..
        } if matches!(arguments.as_slice(), [CallArg {
            value: Expr::DelimitedCall {
                delimiter: GroupDelimiter::Angle,
                ..
            },
            ..
        }])
    ));
}

#[test]
fn parses_angle_type_applications_and_official_type_forms() {
    let program = parse(
        "let apply = { <t: type>(value: Borrow<mut><Option<Array<t><2>>>): with<io>(t): Ptr<t> }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        function.effects.compile_group_delimiters,
        [GroupDelimiter::Angle]
    );
    assert_eq!(
        function.effects.group_delimiters,
        [GroupDelimiter::Parenthesis]
    );
    assert!(matches!(
        &function.groups[0][0].ty,
        Type::Borrow { mutable: true, pointee, .. }
            if matches!(pointee.as_ref(), Type::Named(name, arguments)
                if name == "Option" && matches!(arguments.as_slice(), [Type::ArrayApplication { .. }]))
    ));
    assert!(matches!(
        &function.return_type,
        Some(Type::Function { effects, .. })
            if effects.custom == [Type::Named("io".into(), Vec::new())]
    ));
}

#[test]
fn rejects_parenthesized_type_trait_effect_associated_and_schema_applications() {
    for source in [
        "let read = (value: Option(i32)): i32 { 0 }\n",
        "let cell = <t: type> struct { value: t }\nlet read = (value: cell(i32)): i32 { 0 }\n",
        "let marker = <t: type>(self: type) trait {}\nextend(i32, marker(i32)) {}\n",
        "let state = <t: type> effect {}\nlet read = with<state(i32)>(): i32 { 0 }\n",
        "let read = (value: Chain.Rebind(i32)): i32 { 0 }\n",
        "let handle = <Value: type, Answer: type> ...Clauses(Value, Answer) (value: Value): Answer\n",
    ] {
        assert!(parse(source).is_err(), "legacy application parsed: {source}");
    }
}

#[test]
fn effect_operations_retain_declared_runtime_group_delimiters() {
    let program = parse("let state = effect { get: []: i32; put: {value: i32}: () }\n")
        .expect("effect operation delimiters");
    let Item::Effect(effect) = &program.items[0] else {
        panic!("expected effect");
    };
    assert_eq!(
        effect.operations[0].effects.group_delimiters,
        [GroupDelimiter::Square]
    );
    assert_eq!(
        effect.operations[1].effects.group_delimiters,
        [GroupDelimiter::Brace]
    );
}

#[test]
fn ordinary_user_source_allows_pascal_case_names() {
    parse("let Service = { (): i32 =>  42 }\nlet Answer: i32 = Service()\n")
        .expect("ordinary user declarations are not subject to official API naming checks");
}

#[test]
fn with_callable_operands_are_parenthesized() {
    parse("let apply = { <t: type>(): with<io>(t): t }\n")
        .expect("a parenthesized callable operand is canonical");
    for source in [
        "let apply = <t: type>(): with<io>[(t): t]\n",
        "let apply = with<io><t: type>():{(t): t}\n",
    ] {
        assert!(
            parse(source).is_err(),
            "non-parenthesized operand parsed: {source}"
        );
    }
}

#[test]
fn preserves_multiple_compile_parameters_in_one_group() {
    let program = parse("let choose = { <t: type, u: type>(value: t): u =>  value }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected generic function");
    };
    assert_eq!(function.compile_groups.len(), 1);
    assert_eq!(
        function.compile_groups[0]
            .iter()
            .map(|param| param.name.as_str())
            .collect::<Vec<_>>(),
        vec!["t", "u"]
    );
}

#[test]
fn parses_generic_structs_and_enums() {
    let program = parse(
        "let cell = <t: type> struct { value: t }\n\
             let maybe = <t: type> enum {\n\
             some(t),\n\
             named { value: t },\n\
             none,\n\
             }\n",
    )
    .unwrap();

    let Item::Struct(cell) = &program.items[0] else {
        panic!("expected generic struct");
    };
    assert_eq!(cell.compile_groups[0][0].name, "t");
    assert_eq!(cell.fields[0].ty, Type::Named("t".into(), Vec::new()));

    let Item::Enum(maybe) = &program.items[1] else {
        panic!("expected generic enum");
    };
    assert_eq!(maybe.compile_groups[0][0].kind, Sort::Type);
    assert!(matches!(
        &maybe.variants[0].fields,
        VariantFields::Positional(types)
            if types == &vec![Type::Named("t".into(), Vec::new())]
    ));
    assert!(matches!(
        &maybe.variants[1].fields,
        VariantFields::Named(fields)
            if fields[0].ty == Type::Named("t".into(), Vec::new())
    ));
}

#[test]
fn parses_c_struct_representation_independently_of_derives() {
    let program = parse(
        "let timespec = struct(c) { seconds: i64, nanoseconds: i64 }\n\
             let pair = struct(derive: Copyable, c) { left: i32, right: i32 }\n",
    )
    .unwrap();

    let Item::Struct(timespec) = &program.items[0] else {
        panic!("expected c representation struct");
    };
    assert_eq!(timespec.representation, StructRepresentation::C);
    assert!(timespec.derives.is_empty());

    let Item::Struct(pair) = &program.items[1] else {
        panic!("expected derived c representation struct");
    };
    assert_eq!(pair.representation, StructRepresentation::C);
    assert_eq!(pair.derives, ["Copyable"]);

    for source in [
        "let bad = struct(system) { value: i32 }\n",
        "let bad = struct(c, c) { value: i32 }\n",
    ] {
        let error = parse(source).unwrap_err();
        assert!(error.message.contains("struct representation"));
    }
}

#[test]
fn parses_empty_structs_as_types_that_can_be_extended() {
    let program = parse(
        "let marker = struct {}\n\
             extend(marker) {\n\
             let answer = { (): i32 =>  42 }\n\
             }\n",
    )
    .unwrap();

    let Item::Struct(marker) = &program.items[0] else {
        panic!("expected empty struct");
    };
    assert_eq!(marker.name, "marker");
    assert!(marker.fields.is_empty());

    let Item::Extend(extension) = &program.items[1] else {
        panic!("expected extension");
    };
    assert_eq!(
        extension.target,
        Type::Named("marker".to_owned(), Vec::new())
    );
    assert_eq!(extension.members.len(), 1);

    let error = parse("let namespace = struct { let value = 42 }\n").unwrap_err();
    assert!(
        error.message.contains("expected a field name"),
        "{}",
        error.message
    );
}

#[test]
fn parses_trait_method_signatures_and_associated_types() {
    let program = parse(
        "let foo = trait {\n\
             f: (self: Borrow<self>)(x: i32): i32;\n\
             item: type\n\
             }\n",
    )
    .unwrap();

    let Item::Trait(definition) = &program.items[0] else {
        panic!("expected trait definition");
    };
    assert_eq!(definition.name, "foo");
    assert!(definition.compile_groups.is_empty());
    assert_eq!(definition.members.len(), 2);

    let TraitMember::Function(function) = &definition.members[0] else {
        panic!("expected trait function");
    };
    assert_eq!(function.name, "f");
    assert!(function.compile_groups.is_empty());
    assert_eq!(function.groups.len(), 2);
    assert_eq!(function.groups[0][0].name, "self");
    assert_eq!(function.groups[0][0].mode, PassMode::Inferred);
    assert_eq!(
        function.groups[0][0].ty,
        Type::Borrow {
            mutable: false,
            access: None,
            region: None,
            pointee: Box::new(Type::Named("self".into(), Vec::new())),
        }
    );
    assert_eq!(function.groups[1][0].name, "x");
    assert_eq!(function.groups[1][0].ty, Type::I32);
    assert_eq!(function.return_type, Some(Type::I32));
    assert_eq!(function.body, None);

    let TraitMember::AssociatedType {
        name,
        compile_groups,
        default,
        ..
    } = &definition.members[1]
    else {
        panic!("expected associated type");
    };
    assert_eq!(name, "item");
    assert!(compile_groups.is_empty());
    assert_eq!(default, &None);
}

#[test]
fn parses_colon_trait_and_effect_members_without_semicolons() {
    parse(
        "let overdraft = effect {\n\
         reject: (): never\n\
         }\n\
         let Account = trait {\n\
         credit: (self: Borrow<mut><self>)(amount: i32): ()\n\
         debit: (self: Borrow<mut><self>)(amount: i32): ()\n\
         snapshot: (self: Borrow<self>)(): i32\n\
         }\n",
    )
    .expect("trait and effect callable declarations should be newline-delimited");
}

#[test]
fn preserves_generic_traits_and_trait_member_defaults() {
    let program = parse(
        "let convert = <t: type> trait {\n\
             convert: <u: type>(self: Borrow<self>)(value: u): t = value\n\
             output: <v: type>: type\n\
             }\n",
    )
    .unwrap();

    let Item::Trait(definition) = &program.items[0] else {
        panic!("expected generic trait definition");
    };
    assert_eq!(definition.compile_groups.len(), 1);
    assert_eq!(definition.compile_groups[0][0].name, "t");

    let TraitMember::Function(function) = &definition.members[0] else {
        panic!("expected default method");
    };
    assert_eq!(function.compile_groups[0][0].name, "u");
    assert_eq!(
        function.return_type,
        Some(Type::Named("t".into(), Vec::new()))
    );
    assert_eq!(function.body, Some(Expr::Name("value".into())));

    let TraitMember::AssociatedType {
        name,
        compile_groups,
        default,
        ..
    } = &definition.members[1]
    else {
        panic!("expected generic associated type");
    };
    assert_eq!(name, "output");
    assert_eq!(compile_groups[0][0].name, "v");
    assert_eq!(default, &None);
}

#[test]
fn preserves_region_and_access_generic_associated_type_groups() {
    let program = parse(
        "let lend = trait {\n\
             item: <a: access><r: region>: type\n\
             view: <a: access, r: region>(self: Borrow<a><r><self>)(): item<a><r>\n\
             }\n",
    )
    .unwrap();
    let Item::Trait(definition) = &program.items[0] else {
        panic!("expected trait");
    };
    let TraitMember::AssociatedType { compile_groups, .. } = &definition.members[0] else {
        panic!("expected generic associated type");
    };
    assert_eq!(compile_groups.len(), 2);
    assert_eq!(compile_groups[0][0].kind, Sort::Named("access".into()));
    assert_eq!(compile_groups[1][0].kind, Sort::Region);
}

#[test]
fn rejects_let_on_associated_types() {
    let error = parse("let broken = trait { let item: type }\n").unwrap_err();
    assert!(error.message.contains("trait members omit `let`"));
}

#[test]
fn rejects_unsupported_associated_defaults() {
    let ty = parse("let broken = trait { Item: type = i32 }\n").unwrap_err();
    assert!(ty.message.contains("default associated types"), "{ty:?}");

    let parameters = parse("let broken = trait { Args: <T: type>: parameters = T }\n").unwrap_err();
    assert!(
        parameters
            .message
            .contains("default associated parameter schemas"),
        "{parameters:?}"
    );
}

#[test]
fn trait_members_require_a_terminal_separator() {
    let error = parse("let broken = trait {\nfirst:\n(): i32 second: (): i32\n}\n").unwrap_err();
    assert!(
        error.message.contains("expected a newline or `;`"),
        "{error:?}"
    );
}

#[test]
fn requires_remains_available_as_a_trait_member_name() {
    parse("let protocol = trait {\nfirst: (): i32\nrequires: (): i32\n}\n")
        .expect("contextual `requires` should remain a valid member name");
}

#[test]
fn parses_parenthesized_associated_constant_values() {
    let program = parse(
        "let future = trait { Output: type }\n\
         let step = struct {}\n\
         extend(step, future) { let Output = (); }\n",
    )
    .unwrap();
    let Item::Extend(definition) = &program.items[2] else {
        panic!("expected extension");
    };
    let ExtendMember::Const(binding) = &definition.members[0] else {
        panic!("expected associated constant");
    };
    assert!(matches!(binding.value, Expr::Unit));
}

#[test]
fn rejects_removed_underscore_inference_syntax() {
    for source in [
        "let value = : cell<_> cell<i32>{ value: 20 }\n",
        "let value = cell<_>{ value: 20 }\n",
        "let value = cell<cell<_>>{ value: cell<i32>{ value: 20 } }\n",
        "let value = _\n",
        "let value = : Array<i32><_> []\n",
    ] {
        let error = parse(source).unwrap_err();
        assert!(error.message.contains("`_`"));
        assert!(error.message.contains("inference") || error.message.contains("inferred"));
    }
}

#[test]
fn parses_unsafe_raw_pointer_dereference_and_assignment() {
    let program = parse(
            "let main = { (): i32 => \n  let mut value = 41\n  let pointer = ptr<mut>(borrow<mut>(value))\n  unsafe {\n    *pointer = *pointer + 1\n  }\n  value\n}\n",
        )
        .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, _)) = &function.body else {
        panic!("expected function block");
    };
    let Stmt::Expr(unsafe_expression) = &statements[2] else {
        panic!("expected unsafe expression");
    };
    assert!(matches!(
        unsafe_expression.unlocated(),
        Expr::Unsafe(body)
            if matches!(body.as_ref(), Expr::DoBlock { body } if matches!(
                body.as_ref(), Expr::Block(_, Some(tail)) if matches!(
                    tail.unlocated(),
                    Expr::Assign(left, _)
                        if matches!(left.as_ref(), Expr::Unary(UnaryOp::Deref, _))
                )
            ))
    ));

    let program = parse("let main = { (): () =>  unsafe(do {}) }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(matches!(
        function_tail(function),
        Expr::Call(callee, arguments)
            if matches!(callee.as_ref(), Expr::Name(name) if name == "unsafe")
                && matches!(arguments.as_slice(), [CallArg {
                    value: Expr::DoBlock { .. },
                    ..
                }])
    ));
}

#[test]
fn keeps_generic_construction_and_variant_heads_as_angle_postfix_expressions() {
    fn argument(label: Option<&str>, value: Expr) -> CallArg {
        CallArg {
            label: label.map(str::to_owned),
            value,
        }
    }

    fn type_head(name: &str, type_argument: Expr) -> Expr {
        Expr::DelimitedCall {
            callee: Box::new(Expr::Name(name.to_owned())),
            delimiter: GroupDelimiter::Angle,
            arguments: vec![argument(None, type_argument)],
        }
    }

    let program = parse(
        "let cell = cell<i32>{ value: 42 }\n\
             let nested = cell<cell<i32>>{ value: 42 }\n\
             let some = maybe<i32>.Some(42)\n\
             let none = maybe<i32>.None\n",
    )
    .unwrap();

    let Item::Global(cell) = &program.items[0] else {
        panic!("expected cell binding");
    };
    assert_eq!(
        cell.value,
        Expr::DelimitedCall {
            callee: Box::new(type_head("cell", Expr::Name("i32".into()))),
            delimiter: GroupDelimiter::Brace,
            arguments: vec![argument(Some("value"), Expr::Integer(42))],
        }
    );

    let Item::Global(nested) = &program.items[1] else {
        panic!("expected nested cell binding");
    };
    assert_eq!(
        nested.value,
        Expr::DelimitedCall {
            callee: Box::new(type_head(
                "cell",
                type_head("cell", Expr::Name("i32".into())),
            )),
            delimiter: GroupDelimiter::Brace,
            arguments: vec![argument(Some("value"), Expr::Integer(42))],
        }
    );

    let Item::Global(some) = &program.items[2] else {
        panic!("expected some binding");
    };
    assert_eq!(
        some.value,
        Expr::Call(
            Box::new(Expr::Member(
                Box::new(type_head("maybe", Expr::Name("i32".into()))),
                "Some".into(),
            )),
            vec![argument(None, Expr::Integer(42))],
        )
    );

    let Item::Global(none) = &program.items[3] else {
        panic!("expected none binding");
    };
    assert_eq!(
        none.value,
        Expr::Member(
            Box::new(type_head("maybe", Expr::Name("i32".into()))),
            "None".into(),
        )
    );
}

#[test]
fn adjacent_braces_are_delimited_calls_even_when_empty_or_labeled() {
    let program = parse(
        "let cell = wrapper<i32>{ value: 42 }\n\
             let empty = marker{}\n",
    )
    .unwrap();

    let Item::Global(cell) = &program.items[0] else {
        panic!("expected cell binding");
    };
    assert!(matches!(
        &cell.value,
        Expr::DelimitedCall { callee: constructor, delimiter: GroupDelimiter::Brace, arguments: fields }
            if matches!(
                constructor.as_ref(),
                Expr::DelimitedCall {
                    delimiter: GroupDelimiter::Angle,
                    arguments,
                    ..
                } if arguments.len() == 1
            ) && fields.len() == 1
    ));

    let Item::Global(empty) = &program.items[1] else {
        panic!("expected empty binding");
    };
    assert!(matches!(
        &empty.value,
        Expr::DelimitedCall { callee: constructor, delimiter: GroupDelimiter::Brace, arguments }
            if constructor.as_ref() == &Expr::Name("marker".into())
                && matches!(arguments.as_slice(), [CallArg { label: None, value: Expr::Closure(parameters, _) }] if parameters.is_empty())
    ));
}

#[test]
fn tight_and_spaced_braces_are_the_same_delimited_application() {
    let tight = parse("let value = marker{ field: 42 }\n").unwrap();
    let program = parse("let value = marker { field: 42 }\n").unwrap();
    assert_eq!(tight.items, program.items);
    let Item::Global(binding) = &program.items[0] else {
        panic!("expected value binding");
    };
    assert!(matches!(
        binding.value,
        Expr::DelimitedCall {
            delimiter: GroupDelimiter::Brace,
            ..
        }
    ));
}

#[test]
fn spaced_brace_application_in_for_iterable_stops_before_pattern_body() {
    let source =
        "let visit = { (): () => for (counter { current: 0, end: 4 }) { value -> value } }\n";
    parse(source).expect("the parenthesized Brace application must stop before the `for` body");
}

#[test]
fn brace_argument_lists_accept_positional_and_mixed_arguments() {
    let tight = parse("let value = pair{1, right: 2}\n").unwrap();
    let spaced = parse("let value = pair { 1, right: 2 }\n").unwrap();
    assert_eq!(tight.items, spaced.items);

    let Item::Global(binding) = &tight.items[0] else {
        panic!("expected value binding");
    };
    assert!(matches!(
        &binding.value,
        Expr::DelimitedCall { delimiter: GroupDelimiter::Brace, arguments, .. }
            if matches!(arguments.as_slice(), [
                CallArg { label: None, value: Expr::Integer(1) },
                CallArg { label: Some(label), value: Expr::Integer(2) },
            ] if label == "right")
    ));

    let positional = parse("let value = pair { 1, 2 }\n").unwrap();
    let Item::Global(binding) = &positional.items[0] else {
        panic!("expected value binding");
    };
    assert!(matches!(
        &binding.value,
        Expr::DelimitedCall { delimiter: GroupDelimiter::Brace, arguments, .. }
            if arguments.len() == 2 && arguments.iter().all(|argument| argument.label.is_none())
    ));
}

#[test]
fn nested_commas_and_labels_do_not_make_a_brace_body_an_argument_list() {
    let program = parse(
        "let value = run {\n\
             let result: i32 = call<i32, i32>(left: 1, right: 2)\n\
             result\n\
             }\n",
    )
    .unwrap();
    let Item::Global(binding) = &program.items[0] else {
        panic!("expected value binding");
    };
    assert!(matches!(
        &binding.value,
        Expr::DelimitedCall { delimiter: GroupDelimiter::Brace, arguments, .. }
            if matches!(arguments.as_slice(), [CallArg { label: None, value: Expr::Closure(parameters, _) }] if parameters.is_empty())
    ));
}

#[test]
fn brace_calls_work_in_guards_and_parenthesized_for_iterables() {
    parse(
        "let value = match(input) { item if predicate{1} => item }\n\
             let mapped = for (make { x => x }) { item -> item }\n",
    )
    .expect("brace calls are unambiguous in guards and parenthesized iterables");
}

#[test]
fn directly_ambiguous_for_iterable_brace_bodies_require_parentheses() {
    let error = parse("let value = for make { x => x } { item -> item }\n").unwrap_err();
    assert!(error
        .message
        .contains("parenthesize the iterable application"));
}

#[test]
fn a_block_comment_newline_prevents_brace_attachment() {
    let error = parse("let value = predicate /* hidden\nnewline */ { 1 }\n").unwrap_err();
    assert!(error.message.contains("expected") || error.message.contains("found"));
}

#[test]
fn rejects_misordered_compile_parameter_groups_and_compile_sorts_at_runtime() {
    let cases = [
        (
            "let bad = { (value: i32)<t: type>: i32 =>  value }\n",
            "must precede runtime parameter groups",
        ),
        (
            "let bad = { (value: t, u: type): t =>  value }\n",
            "runtime parameter groups cannot contain compile-time binders",
        ),
    ];

    for (source, expected) in cases {
        let error = parse(source).unwrap_err();
        assert!(
            error.message.contains(expected),
            "expected `{expected}` in `{}`",
            error.message
        );
    }
}

#[test]
fn rejects_reserved_compile_parameter_names() {
    for name in [
        "_", "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128",
        "usize", "bool", "never",
    ] {
        let source = format!("let invalid = {{ <{name}: type>(value: i32): i32 => value }}\n");
        let error = parse(&source).unwrap_err();
        assert_eq!(
            error.message,
            format!("reserved type name `{name}` cannot be used as a compile-time parameter")
        );
        assert_eq!((error.line, error.column), (1, 18));
    }
}

#[test]
fn parses_bounded_c_foreign_declarations() {
    let program = parse(
        "pub let c_abs = { (value: i32): i32 => foreign(c, \"abs\") }\n\
             let strlen = { (value: Ptr<u8>): usize => foreign(c) }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected foreign function");
    };
    let foreign = function.foreign.as_ref().expect("foreign metadata");
    assert_eq!(foreign.abi, ForeignAbi::C);
    assert_eq!(foreign.link_name, "abs");
    assert!(function.effects.unsafety);
    assert!(function.body.is_none());
    assert_eq!(function.groups.len(), 1);
    assert_eq!(program.item_visibilities[0], Visibility::Public);

    let Item::Function(default_symbol) = &program.items[1] else {
        panic!("expected foreign function with default symbol");
    };
    assert_eq!(
        default_symbol
            .foreign
            .as_ref()
            .expect("foreign metadata")
            .link_name,
        "strlen"
    );

    let legacy = parse("extern \"c\" { let abs = (value: i32): i32 }\n").unwrap_err();
    assert!(legacy.message.contains("grouped `extern`"));

    for (source, expected) in [
        (
            "let value = foreign(c)\n",
            "requires one runtime parameter group",
        ),
        (
            "let identity = { <t: type>(value: t): t => foreign(c) }\n",
            "no compile groups",
        ),
        (
            "let abs = { (value: i32) => foreign(c) }\n",
            "an explicit result type",
        ),
        (
            "let abs = { with<unsafety>(value: i32): i32 => foreign(c) }\n",
            "cannot declare effects",
        ),
        (
            "let abs = { (value: i32): i32 => foreign(c, \"\") }\n",
            "non-empty ASCII linker symbol",
        ),
    ] {
        let error = parse(source).unwrap_err();
        assert!(
            error.message.contains(expected),
            "`{source}` did not report `{expected}`: {}",
            error.message
        );
    }
}

#[test]
fn rejects_runtime_parameters_on_generic_data_and_legacy_extend_headers() {
    let data = parse("let bad = <t: type>(value: t) struct { value: t }\n").unwrap_err();
    assert!(data.message.contains("outer callable brace"));

    let extension = parse("extend cell {}\n").unwrap_err();
    assert!(extension.message.contains("`(` after `extend`"));
}

#[test]
fn keeps_call_groups_nested() {
    let program = parse("let main = { (): i32 =>  add(1)(2) }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Expr::Call(inner, second) = function_tail(function) else {
        panic!("expected outer call");
    };
    assert!(matches!(
        second.as_slice(),
        [CallArg {
            label: None,
            value: Expr::Integer(2)
        }]
    ));
    assert!(matches!(
        inner.as_ref(),
        Expr::Call(_, first)
            if matches!(
                first.as_slice(),
                [CallArg {
                    label: None,
                    value: Expr::Integer(1)
                }]
            )
    ));
}

#[test]
fn allows_newlines_inside_a_named_function_header() {
    let program = parse(
        "let add = { (x: i32)\n\
             (y: i32)\n\
             : i32\n\
             => x + y }\n",
    )
    .unwrap();

    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(function.groups.len(), 2);
    assert_eq!(function.return_type, Some(Type::I32));
}

#[test]
fn newline_does_not_continue_an_expression_call() {
    let program = parse(
        "let main = { (): i32 => \n\
             add(1)\n\
             (2)\n\
             }\n",
    )
    .unwrap();

    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, Some(tail))) = &function.body else {
        panic!("expected block");
    };
    let [Stmt::Expr(expression)] = statements.as_slice() else {
        panic!("expected one expression statement");
    };
    assert!(matches!(
        expression.unlocated(),
        Expr::Call(_, arguments)
            if matches!(
                arguments.as_slice(),
                [CallArg {
                    label: None,
                    value: Expr::Integer(1)
                }]
            )
    ));
    assert_eq!(tail.unlocated(), &Expr::Integer(2));
}

#[test]
fn parses_local_bindings_assignment_and_block_tail() {
    let program = parse(
        "let main = { (): i32 => \n\
             let mut x = 2\n\
             x = x * 3 + 1\n\
             x\n\
             }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Expr::Block(statements, Some(tail)) = function.body.as_ref().unwrap() else {
        panic!("expected block with a tail value");
    };
    assert_eq!(statements.len(), 2);
    assert_eq!(tail.unlocated(), &Expr::Name("x".into()));
}

#[test]
fn semicolon_discards_the_last_block_value() {
    let program = parse("let main = { (): () =>  1; }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(matches!(function.body, Some(Expr::Block(_, None))));
}

#[test]
fn parses_arithmetic_compound_assignments() {
    let program = parse(
        "let main = { (): () => \n\
             let mut value = 1\n\
             value += 2\n\
             value -= 3\n\
             value *= 4\n\
             value /= 5\n\
             value %= 6\n\
             value &= 7\n\
             value |= 8\n\
             value ^= 9\n\
             value <<= 1\n\
             value >>= 1\n\
             ()\n\
             }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Expr::Block(statements, Some(tail)) = function.body.as_ref().unwrap() else {
        panic!("expected block");
    };
    assert_eq!(tail.unlocated(), &Expr::Unit);
    for (statement, operator) in statements[1..].iter().zip([
        BinaryOp::Add,
        BinaryOp::Sub,
        BinaryOp::Mul,
        BinaryOp::Div,
        BinaryOp::Rem,
        BinaryOp::BitAnd,
        BinaryOp::BitOr,
        BinaryOp::BitXor,
        BinaryOp::Shl,
        BinaryOp::Shr,
    ]) {
        let Stmt::Expr(expression) = statement else {
            panic!("expected compound assignment");
        };
        assert!(matches!(
            expression.unlocated(),
            Expr::CompoundAssign(_, found, _) if *found == operator
        ));
    }
}

#[test]
fn parses_do_if_else_and_return() {
    let program = parse(
        "let choose = { (flag: bool): i32 =>  do {\n\
             if(flag) { return(1) } else: { 2 }\n\
             } }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(matches!(function_tail(function), Expr::DoBlock { .. }));
}

#[test]
fn rejects_removed_if_let_syntax() {
    let error = parse(
        "let choose = { (value: Option<i32>): i32 => \n\
             if let some(found) = value { found } else: { 0 }\n\
             }\n",
    )
    .unwrap_err();
    assert!(!error.message.is_empty());
}

#[test]
fn parses_throw_as_a_core_error_function() {
    let program = parse("let fail = { (): Result<bool><i32> =>  throw(false) }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        function_tail(function),
        &Expr::Call(
            Box::new(Expr::Member(
                Box::new(Expr::Member(
                    Box::new(Expr::Name("core".to_owned())),
                    "error".to_owned(),
                )),
                "throw".to_owned(),
            )),
            vec![CallArg {
                label: None,
                value: Expr::Bool(false),
            }],
        )
    );

    assert!(parse("let fail = { (): Result<bool><i32> =>  throw false }\n").is_err());
}

#[test]
fn parses_do_and_try_as_distinct_immediate_handlers() {
    let program = parse(
        "let main = { (): Result<bool><i32> =>  try { 42 } }\n\
             let other = { (): i32 =>  do { 42 } }\n",
    )
    .unwrap();
    let Item::Function(main) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(matches!(function_tail(main), Expr::Try(_)));
    let Item::Function(other) = &program.items[1] else {
        panic!("expected function");
    };
    assert!(matches!(function_tail(other), Expr::DoBlock { .. }));

    let member = parse(
        "let unwrap = { with<throwing<bool>>(value: Result<bool><i32>): i32 =>  value.try }\n",
    )
    .unwrap();
    let Item::Function(member) = &member.items[0] else {
        panic!("expected function");
    };
    assert!(matches!(
        function_tail(member),
        Expr::Member(_, name) if name == "try"
    ));

    let program = parse("let value = : Result<bool><i32> try(do { 42 })\n").unwrap();
    let Item::Global(binding) = &program.items[0] else {
        panic!("expected global");
    };
    assert!(matches!(
        &binding.value,
        Expr::Call(callee, arguments)
            if matches!(callee.as_ref(), Expr::Name(name) if name == "try")
                && matches!(arguments.as_slice(), [CallArg {
                    value: Expr::DoBlock { .. },
                    ..
                }])
    ));
}

#[test]
fn rejects_parenthesis_free_ordinary_calls() {
    for source in [
        "let value = transform input\n",
        "let value = combine(left) right\n",
        "let value = receiver.shift amount\n",
    ] {
        assert!(parse(source).is_err(), "bare call parsed: {source}");
    }
}

#[test]
fn brace_body_creates_a_brace_call_group() {
    let program = parse("let value = map(items) { (x: i32) => { x + 1 } }\n").unwrap();
    let Item::Global(binding) = &program.items[0] else {
        panic!("expected global");
    };
    let Expr::DelimitedCall {
        callee: first_call,
        delimiter,
        arguments,
    } = &binding.value
    else {
        panic!("expected brace call group");
    };
    assert_eq!(*delimiter, GroupDelimiter::Brace);
    assert_eq!(arguments.len(), 1);
    assert!(matches!(first_call.as_ref(), Expr::Call(_, _)));
}

#[test]
fn brace_body_can_supply_the_first_call_group() {
    let program = parse("let invoke = { (): () =>  run { cleanup() } }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Expr::DelimitedCall {
        callee,
        delimiter,
        arguments,
    } = function_tail(function)
    else {
        panic!("expected brace call");
    };
    assert_eq!(*delimiter, GroupDelimiter::Brace);
    assert!(matches!(callee.as_ref(), Expr::Name(name) if name == "run"));
    let [argument] = arguments.as_slice() else {
        panic!("expected one closure argument");
    };
    assert!(matches!(argument.value.unlocated(), Expr::Closure(_, _)));
}

#[test]
fn multiple_brace_groups_create_successive_delimited_calls() {
    let program = parse("let value = choose() { true } { 1 }\n").unwrap();
    let Item::Global(binding) = &program.items[0] else {
        panic!("expected global");
    };
    let Expr::DelimitedCall {
        callee: second_call,
        delimiter: second_delimiter,
        arguments: second_group,
    } = &binding.value
    else {
        panic!("expected second brace group");
    };
    assert_eq!(*second_delimiter, GroupDelimiter::Brace);
    assert_eq!(second_group.len(), 1);
    let Expr::DelimitedCall {
        callee: first_call,
        delimiter: first_delimiter,
        arguments: first_group,
    } = second_call.as_ref()
    else {
        panic!("expected first brace group");
    };
    assert_eq!(*first_delimiter, GroupDelimiter::Brace);
    assert_eq!(first_group.len(), 1);
    assert!(matches!(first_call.as_ref(), Expr::Call(_, arguments) if arguments.is_empty()));
}

#[test]
fn removed_named_closure_attachments_do_not_parse_as_calls() {
    assert!(parse("let value = choose() condition: { true } body: { 1 }\n").is_err());
}

#[test]
fn handler_member_accepts_one_labeled_brace_call() {
    let program = parse(
        "let run = { (): i32 => \n\
             ask.handle(ask.value()) {\n\
             value(resume) => resume(42),\n\
             Return(answer) => answer,\n\
             }\n\
             }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(_, Some(value))) = &function.body else {
        panic!("expected function body");
    };
    let Expr::DelimitedCall {
        callee,
        delimiter,
        arguments,
    } = value.unlocated()
    else {
        panic!("expected brace-delimited handler call");
    };
    assert_eq!(*delimiter, GroupDelimiter::Brace);
    assert_eq!(
        arguments
            .iter()
            .map(|argument| argument.label.as_deref())
            .collect::<Vec<_>>(),
        [Some("value"), Some("done"), Some("action")]
    );
    assert!(matches!(callee.as_ref(), Expr::Member(_, member) if member == "handle"));
}

#[test]
fn handler_brace_call_ends_before_the_following_statement() {
    let program = parse(
        "let run = { (): i32 => \n\
             let ignored = iteration_skip.handle(()) {\n\
             next() => (),\n\
             }\n\
             if(true) { 42 } else: { 0 }\n\
             }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, Some(tail))) = &function.body else {
        panic!("expected function body");
    };
    assert_eq!(statements.len(), 1);
    let (condition, then_branch, else_branch) = if_call_parts(tail);
    assert_eq!(condition, &Expr::Bool(true));
    assert!(matches!(then_branch, Expr::Block(_, _)));
    assert!(matches!(else_branch, Expr::Block(_, _)));
}

#[test]
fn rejects_old_successive_handler_groups() {
    assert!(parse(
        "let run = { (): i32 =>  ask.handle value: (resume) => { resume(42) } action: { ask.value() } }\n"
    )
    .is_err());
}

#[test]
fn rejects_colonless_named_brace_attachments() {
    for source in [
        "let value = choose() condition { true }\n",
        "let run = { (): i32 =>  ask.handle get (resume) => { resume(42) } action { ask.get() } }\n",
    ] {
        assert!(parse(source).is_err(), "colonless label parsed: {source}");
    }
}

#[test]
fn every_brace_expression_is_a_closure() {
    let program = parse(
        "let answer = { 42 }\n\
             let parenthesized = { (40 + 2) }\n\
             let successor: callable = { (value: i32) => value + 1 }\n",
    )
    .unwrap();

    for item in &program.items[..2] {
        let Item::Global(binding) = item else {
            panic!("expected closure-valued global");
        };
        assert!(matches!(
            binding.value,
            Expr::Closure(ref parameters, _) if parameters.is_empty()
        ));
    }
    let Item::Global(successor) = &program.items[2] else {
        panic!("expected parameterized closure");
    };
    assert!(matches!(
        successor.value,
        Expr::Closure(ref parameters, _) if parameters.len() == 1
    ));

    let removed = parse("let old = { -> 42 }\n").unwrap_err();
    assert!(removed.message.contains("do not use `->`"));
}

#[test]
fn parses_unified_callable_signatures_and_expression_bodies() {
    let program = parse(
        "let add = { (x: i32, y: i32): i32 => x + y }\n\
         let invoke = { (action: (i32): i32): i32 => action(42) }\n\
         let main = { (): i32 => \n\
           let increment = { (value: i32): i32 => value + 1 }\n\
           invoke(increment)\n\
         }\n",
    )
    .unwrap();

    let Item::Function(add) = &program.items[0] else {
        panic!("expected named callable");
    };
    assert!(matches!(
        function_tail(add),
        Expr::Binary(_, BinaryOp::Add, _)
    ));
    let Item::Function(invoke) = &program.items[1] else {
        panic!("expected invoke callable");
    };
    let Type::Function { result, .. } = &invoke.groups[0][0].ty else {
        panic!("expected colon-style callable type");
    };
    assert_eq!(result.as_ref(), &Type::I32);
}

#[test]
fn rejects_pre_arrow_callable_syntax() {
    let named = parse("let f = { (): i32 1 }\n").expect_err("named bodies require `=>`");
    assert!(named.message.contains("expected `=>`"), "{named:?}");

    let closure =
        parse("let closure = { (x:i32) {x} }\n").expect_err("parameterized closures require `=>`");
    assert!(closure.message.contains("expected `=>`"), "{closure:?}");

    let callable_type = parse("let apply = { (action: (i32) {i32}): i32 }\n")
        .expect_err("callable type results require `:`");
    assert!(
        callable_type
            .message
            .contains("callable types require `:` before their result type"),
        "{callable_type:?}"
    );
}

#[test]
fn brace_call_accepts_a_parameterized_callable_block() {
    let program = parse(
        "let apply = { (value: i32){transform: (i32): i32}: i32 => transform(value) }\n\
         let main = { (): i32 => apply(41) { (item) => item + 1 } }\n",
    )
    .unwrap();
    let Item::Function(main) = &program.items[1] else {
        panic!("expected main callable");
    };
    let Expr::DelimitedCall {
        delimiter,
        arguments,
        ..
    } = function_tail(main)
    else {
        panic!("expected brace call");
    };
    assert_eq!(*delimiter, GroupDelimiter::Brace);
    assert!(matches!(
        arguments.as_slice(),
        [CallArg { label: None, value: Expr::Closure(parameters, _) }] if parameters.len() == 1
    ));
}

#[test]
fn struct_declaration_creates_a_nominal_constructor() {
    let program = parse(
        "let Point = struct { x: i32, y: i32 }\n\
         let main = { (): Point => Point { x: 40, y: 2 } }\n",
    )
    .unwrap();
    let Item::Struct(point) = &program.items[0] else {
        panic!("expected a struct declaration");
    };
    assert_eq!(point.name, "Point");
    assert_eq!(
        point
            .fields
            .iter()
            .map(|field| field.name.as_str())
            .collect::<Vec<_>>(),
        ["x", "y"]
    );
}

#[test]
fn named_callable_declarations_require_fat_arrows() {
    for source in [
        "let answer = { (): i32 42 }\n",
        "extend(cell) { let read = { (self: Borrow<self>)(): i32 self.value } }\n",
    ] {
        let error = parse(source).unwrap_err();
        assert!(error.message.contains("expected `=>`"), "{error:?}");
    }

    parse("let answer = 42\nlet read = { (): i32 => 42 }\n").unwrap();
}

#[test]
fn trait_defaults_require_equals() {
    let error = parse("let read = trait { read: (self: Borrow<self>)(): i32 42 }\n").unwrap_err();
    assert!(
        error
            .message
            .contains("expected a newline or `;` after trait member"),
        "{error:?}"
    );
}

#[test]
fn rejects_legacy_trait_callable_and_effect_operation_syntax() {
    let trait_callable =
        parse("let readable = trait { let read = { (self: Borrow<self>)(): i32 } }\n").unwrap_err();
    assert!(
        trait_callable.message.contains("trait members omit `let`"),
        "{trait_callable:?}"
    );

    let trait_default =
        parse("let readable = trait { read: (self: Borrow<self>)(): i32 => 42 }\n").unwrap_err();
    assert!(
        trait_default
            .message
            .contains("trait default implementations use `=`"),
        "{trait_default:?}"
    );

    let effect_operation = parse("let state = effect { get(): i32 }\n").unwrap_err();
    assert!(
        effect_operation
            .message
            .contains("expected `:` after effect operation name"),
        "{effect_operation:?}"
    );

    for source in [
        "let state = effect { get: (): i32 = 1 }\n",
        "let state = effect {\nget: (): i32\n= 1\n}\n",
    ] {
        let body = parse(source).unwrap_err();
        assert!(
            body.message
                .contains("effect operations cannot have bodies"),
            "{body:?}"
        );
    }
}

#[test]
fn parses_structs_and_enum_field_shapes() {
    let program = parse(
        "let point = struct { x: i32, pub(package) y: i32, pub z: i32 }\n\
             let documented = struct {\n\
             /// a field with a documentation comment.\n\
             value: i32,\n\
             }\n\
             let shape = enum {\n\
             circle { pub radius: i32, pub(package) center: point, label: i32 },\n\
             record {\n\
             /// a named variant field with a documentation comment.\n\
             item: i32,\n\
             },\n\
             pair(i32, i32),\n\
             unit,\n\
             }\n",
    )
    .unwrap();

    let Item::Struct(point) = &program.items[0] else {
        panic!("expected struct");
    };
    assert_eq!(point.name, "point");
    assert_eq!(point.fields.len(), 3);
    assert_eq!(
        point
            .fields
            .iter()
            .map(|field| field.visibility)
            .collect::<Vec<_>>(),
        vec![Visibility::Private, Visibility::Package, Visibility::Public,]
    );

    let Item::Struct(documented) = &program.items[1] else {
        panic!("expected documented struct");
    };
    assert_eq!(documented.fields.len(), 1);
    assert_eq!(documented.fields[0].name, "value");

    let Item::Enum(shape) = &program.items[2] else {
        panic!("expected enum");
    };
    assert_eq!(shape.variants.len(), 4);
    assert!(matches!(
        &shape.variants[0].fields,
        VariantFields::Named(fields)
            if fields
                .iter()
                .map(|field| field.visibility)
                .eq([
                    Visibility::Public,
                    Visibility::Package,
                    Visibility::Private,
                ])
    ));
    assert!(matches!(
        &shape.variants[1].fields,
        VariantFields::Named(fields) if fields.len() == 1 && fields[0].name == "item"
    ));
    assert!(matches!(
        &shape.variants[2].fields,
        VariantFields::Positional(types) if types == &vec![Type::I32, Type::I32]
    ));
    assert_eq!(shape.variants[3].fields, VariantFields::Unit);
}

#[test]
fn parses_labeled_construction_member_access_and_assignment() {
    let program = parse(
        "let point = struct { x: i32, y: i32 }\n\
             let main = { (): i32 => \n\
             let mut point = point{ x: 1, y: 2 }\n\
             point.x = 3\n\
             point.x\n\
             }\n",
    )
    .unwrap();

    let Item::Function(main) = &program.items[1] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, Some(tail))) = &main.body else {
        panic!("expected block");
    };
    let Stmt::Let(binding) = &statements[0] else {
        panic!("expected binding");
    };
    assert!(matches!(
        &binding.value,
        Expr::DelimitedCall { delimiter: GroupDelimiter::Brace, arguments: fields, .. }
            if fields.iter().map(|argument| argument.label.as_deref()).collect::<Vec<_>>()
                == vec![Some("x"), Some("y")]
    ));
    let Stmt::Expr(assignment) = &statements[1] else {
        panic!("expected assignment");
    };
    assert!(matches!(
        assignment.unlocated(),
        Expr::Assign(left, right)
            if matches!(left.as_ref(), Expr::Member(_, field) if field == "x")
                && right.as_ref() == &Expr::Integer(3)
    ));
    assert!(matches!(tail.unlocated(), Expr::Member(_, field) if field == "x"));
}

#[test]
fn parses_match_partial_closure_patterns_and_guards() {
    let program = parse(
        "let classify = { (shape: shape): i32 =>  match(shape) {\n\
             shape.circle(radius: value) if value > 0 => value,\n\
             shape.unit => 0,\n\
             _ => -1,\n\
             } }\n",
    )
    .unwrap();

    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let (input, cases) = match_call_parts(function_tail(function));
    assert_eq!(input, &Expr::Name("shape".into()));
    assert_eq!(cases.len(), 3);
    assert!(cases[0].guard.is_some());
    assert!(matches!(
        &cases[0].pattern,
        Pattern::Constructor { path, fields: PatternFields::Named(fields) }
            if path == &vec!["shape".to_owned(), "circle".to_owned()]
                && fields[0].name == "radius"
    ));
    assert_eq!(cases[2].pattern, Pattern::Wildcard);
}

#[test]
fn rejects_legacy_prefix_and_postfix_match_forms() {
    for source in [
        "let value = input match { _ => 0 }\n",
        "let value = match input { _ => 0 }\n",
        "let value = match input { _ -> 0 }\n",
    ] {
        assert!(parse(source).is_err(), "legacy match parsed: {source}");
    }
}

#[test]
fn parses_coalesce_right_associatively_between_match_and_logical_or() {
    let program = parse(
        "let chain = a || b ??\n  c || d ?? e\n\
             let matched = match(a ?? b) { _ => c }\n\
             let assigned = target = a ?? b\n",
    )
    .unwrap();

    let Item::Global(chain) = &program.items[0] else {
        panic!("expected chain binding");
    };
    assert!(matches!(
        &chain.value,
        Expr::Coalesce(left, right)
            if matches!(left.as_ref(), Expr::Binary(_, BinaryOp::Or, _))
                && matches!(
                    right.as_ref(),
                    Expr::Coalesce(nested_left, nested_right)
                        if matches!(nested_left.as_ref(), Expr::Binary(_, BinaryOp::Or, _))
                            && nested_right.as_ref() == &Expr::Name("e".into())
                )
    ));

    let Item::Global(matched) = &program.items[1] else {
        panic!("expected match binding");
    };
    let (input, cases) = match_call_parts(&matched.value);
    assert!(matches!(input, Expr::Coalesce(_, _)));
    assert_eq!(cases.len(), 1);

    let Item::Global(assigned) = &program.items[2] else {
        panic!("expected assignment binding");
    };
    assert!(matches!(
        &assigned.value,
        Expr::Assign(_, value) if matches!(value.as_ref(), Expr::Coalesce(_, _))
    ));
}

#[test]
fn rejects_positional_arguments_after_labeled_arguments() {
    let error = parse("let value = call(x: 1, 2)\n").unwrap_err();
    assert!(error
        .message
        .contains("positional arguments must precede labeled arguments"));
}

#[test]
fn parses_shared_and_mutable_borrow_places() {
    let program = parse(
        "let main = { (): () => \n\
             let shared = borrow(value.field)\n\
             let exclusive = borrow<mut>(value)\n\
             }\n",
    )
    .unwrap();

    let Item::Function(main) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, None)) = &main.body else {
        panic!("expected block");
    };
    assert!(matches!(
        &statements[0],
        Stmt::Let(Binding {
            value: Expr::Borrow {
                mutable: false,
                value,
                ..
            },
            ..
        }) if matches!(value.as_ref(), Expr::Member(_, field) if field == "field")
    ));
    assert!(matches!(
        &statements[1],
        Stmt::Let(Binding {
            value: Expr::Borrow {
                mutable: true,
                value,
                ..
            },
            ..
        }) if value.as_ref() == &Expr::Name("value".into())
    ));
}

#[test]
fn rejects_borrowing_a_non_place_expression() {
    let error = parse("let invalid = borrow(make())\n").unwrap_err();
    assert!(error.message.contains("name or member chain"));
}

#[test]
fn parses_fixed_array_types_multiline_literals_and_indexes() {
    let program = parse(
        "let read = { (values: Array<i32><2>): i32 => \n\
             let local: Array<i32><3> = [\n\
             40,\n\
             1,\n\
             1,\n\
             ]\n\
             local[0] + local[1]\n\
             }\n",
    )
    .unwrap();

    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        function.groups[0][0].ty,
        Type::ArrayApplication {
            constructor: "Array".to_owned(),
            element: Box::new(Type::I32),
            length: USizeConst::Literal(2),
        }
    );
    let Some(Expr::Block(statements, Some(tail))) = &function.body else {
        panic!("expected block");
    };
    let Stmt::Let(binding) = &statements[0] else {
        panic!("expected local array binding");
    };
    assert_eq!(
        binding.annotation,
        Some(Type::ArrayApplication {
            constructor: "Array".to_owned(),
            element: Box::new(Type::I32),
            length: USizeConst::Literal(3),
        })
    );
    assert!(matches!(&binding.value, Expr::Array(elements) if elements.len() == 3));
    assert!(matches!(
        tail.unlocated(),
        Expr::Binary(left, BinaryOp::Add, right)
            if matches!(left.as_ref(), Expr::Index { .. })
                && matches!(right.as_ref(), Expr::Index { .. })
    ));
}

#[test]
fn parses_pure_static_expressions_in_dependent_array_lengths() {
    let program = parse(
        "let next = { (value: usize): usize =>  value + 1 }\n\
             let consume = { (values: Array<i32><next(2) * 2>): i32 =>  values[0] }\n",
    )
    .unwrap();
    let Item::Function(consume) = &program.items[1] else {
        panic!("expected consume function");
    };
    assert_eq!(
        consume.groups[0][0].ty,
        Type::ArrayApplication {
            constructor: "Array".into(),
            element: Box::new(Type::I32),
            length: USizeConst::Expression(Box::new(StaticExpr::Binary(
                Box::new(StaticExpr::Call {
                    function: "next".into(),
                    groups: vec![vec![StaticCallArg {
                        label: None,
                        value: StaticExpr::USize(2),
                    }]],
                    group_delimiters: vec![GroupDelimiter::Parenthesis],
                }),
                BinaryOp::Mul,
                Box::new(StaticExpr::USize(2)),
            ))),
        }
    );
}

#[test]
fn parses_single_scalar_brace_calls_in_dependent_array_lengths() {
    let program = parse(
        "let count = { {value: usize}: usize =>  value }\n\
             let consume = { (values: Array<i32><count{2}>): i32 =>  values[0] }\n",
    )
    .unwrap();
    let Item::Function(consume) = &program.items[1] else {
        panic!("expected consume function");
    };
    assert!(matches!(
        &consume.groups[0][0].ty,
        Type::ArrayApplication {
            length: USizeConst::Expression(expression),
            ..
        } if matches!(expression.as_ref(), StaticExpr::Call {
            function,
            groups,
            group_delimiters,
        } if function == "count"
            && groups == &vec![vec![StaticCallArg {
                label: None,
                value: StaticExpr::USize(2),
            }]]
            && group_delimiters == &vec![GroupDelimiter::Brace])
    ));
}

#[test]
fn rejects_parenthesized_named_enum_variant_declarations() {
    let error = parse("let choice = enum { named(value: i32) }\n")
        .expect_err("named enum fields require braces");
    assert!(error.message.contains("variant fields"), "{error:?}");
}

#[test]
fn indexed_places_can_be_assigned_and_borrowed() {
    let program = parse(
        "let main = { (): i32 => \n\
             let mut values = [0]\n\
             values[0] = 42\n\
             let item = borrow(values[0])\n\
             values[0]\n\
             }\n",
    )
    .unwrap();

    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, Some(tail))) = &function.body else {
        panic!("expected block");
    };
    let Stmt::Expr(assignment) = &statements[1] else {
        panic!("expected assignment");
    };
    assert!(matches!(
        assignment.unlocated(),
        Expr::Assign(left, _) if matches!(left.as_ref(), Expr::Index { .. })
    ));
    assert!(matches!(
        &statements[2],
        Stmt::Let(Binding {
            value: Expr::Borrow { value, .. },
            ..
        }) if matches!(value.as_ref(), Expr::Index { .. })
    ));
    assert!(matches!(tail.unlocated(), Expr::Index { .. }));
}

#[test]
fn parses_explicit_borrow_types() {
    let program = parse(
        "let main = { (): i32 => \n\
             let value = 42\n\
             let shared: Borrow<i32> = borrow(value)\n\
             let mutable: Borrow<mut><i32> = borrow<mut>(value)\n\
             shared\n\
             }\n",
    )
    .unwrap();

    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, _)) = &function.body else {
        panic!("expected block");
    };
    let Stmt::Let(shared) = &statements[1] else {
        panic!("expected shared borrow binding");
    };
    assert_eq!(
        shared.annotation,
        Some(Type::Borrow {
            mutable: false,
            access: None,
            region: None,
            pointee: Box::new(Type::I32),
        })
    );
    let Stmt::Let(mutable) = &statements[2] else {
        panic!("expected mutable borrow binding");
    };
    assert_eq!(
        mutable.annotation,
        Some(Type::Borrow {
            mutable: true,
            access: None,
            region: None,
            pointee: Box::new(Type::I32),
        })
    );
}

#[test]
fn rejects_legacy_mut_borrow_token_sequence() {
    for source in [
        "let invalid = { (mut value: Borrow<i32>): i32 =>  value }\n",
        "let invalid = { (value: mut borrow(i32)): i32 =>  value }\n",
        "let invalid = { (value: i32): Borrow<i32> =>  mut borrow(value) }\n",
    ] {
        let error = parse(source).unwrap_err();
        assert!(!error.message.is_empty());
    }
}

#[test]
fn parses_region_parameters_and_borrow_regions() {
    let program = parse(
        "let choose = { <r: region>(value: Borrow<r><i32>): Borrow<r><i32> =>  borrow(value) }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(function.compile_groups[0][0].name, "r");
    assert_eq!(function.compile_groups[0][0].kind, Sort::Region);
    assert_eq!(
        function.groups[0][0].ty,
        Type::Borrow {
            mutable: false,
            access: None,
            region: Some("r".to_owned()),
            pointee: Box::new(Type::I32),
        }
    );
    assert_eq!(
        function.return_type,
        Some(Type::Borrow {
            mutable: false,
            access: None,
            region: Some("r".to_owned()),
            pointee: Box::new(Type::I32),
        })
    );
}

#[test]
fn parses_access_parameters_in_borrow_modes_types_and_expressions() {
    let program = parse(
        "let identity = { <a: access, r: region, t: type>\n\
             (value: Borrow<a><r><t>): Borrow<a><r><t> => borrow<a>(value) }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(function.compile_groups[0][0].kind.is_access());
    assert_eq!(
        function.groups[0][0].ty,
        Type::Borrow {
            mutable: false,
            access: Some("a".to_owned()),
            region: Some("r".to_owned()),
            pointee: Box::new(Type::Named("t".into(), Vec::new())),
        }
    );
    assert!(matches!(
        function.return_type,
        Some(Type::Borrow {
            mutable: false,
            access: Some(ref access),
            region: Some(ref region),
            ..
        }) if access == "a" && region == "r"
    ));
    assert!(matches!(
        function_tail(function),
        Expr::Borrow {
            mutable: false,
            access: Some(ref access),
            ..
        } if access == "a"
    ));
}

#[test]
fn parses_closed_types_as_compile_parameter_types() {
    let program = parse(
        "let optimization = enum { size, speed }\n\
             let select = { <b: bool, o: optimization>(value: i32): i32 =>  value }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[1] else {
        panic!("expected a function");
    };
    assert!(matches!(
        &function.compile_groups[0][0].kind,
        Sort::Named(name) if name == "bool"
    ));
    assert!(matches!(
        &function.compile_groups[0][1].kind,
        Sort::Named(name) if name == "optimization"
    ));
}

#[test]
fn parses_string_as_an_ordinary_named_type() {
    let program = parse(
        "let register = { <name: string>(move body: with<core.error.throwing<core.string.String>>(): ()): () => builtin() }\n",
    )
    .unwrap();
    let [Item::Function(function)] = program.items.as_slice() else {
        panic!("expected one metadata function");
    };
    assert_eq!(
        function.compile_groups,
        vec![vec![CompileParam {
            name: "name".to_owned(),
            kind: Sort::Named("string".to_owned()),
            default: None,
        }]]
    );
    assert_eq!(function.groups.len(), 1);

    let runtime = parse("let identity = { (value: string): string =>  value }\n").unwrap();
    let [Item::Function(function)] = runtime.items.as_slice() else {
        panic!("expected one runtime string function");
    };
    assert_eq!(
        function.groups[0][0].ty,
        Type::Named("string".to_owned(), Vec::new())
    );
    assert_eq!(
        function.return_type,
        Some(Type::Named("string".to_owned(), Vec::new()))
    );
}

#[test]
fn parses_parameter_modifier_functions_in_prefix_position() {
    let program = parse(
        "let identity = { <m: <p: parameters>: parameters, t: type>(m value: t): t =>  value }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(function.compile_groups[0][0].kind.is_parameter_modifier());
    assert_eq!(function.groups[0][0].mode, PassMode::Inferred);
    assert_eq!(function.groups[0][0].modifiers, ["m"]);
}

#[test]
fn parses_parameter_prefixes_as_composable_modifiers() {
    let program = parse(
            "let decorate = { <b: bool, m: <p: parameters>: parameters>(b m value: i32): i32 =>  value }\n",
        )
        .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected a function");
    };
    assert_eq!(function.groups[0][0].modifiers, ["b", "m"]);
    assert_eq!(function.groups[0][0].mode, PassMode::Inferred);
}

#[test]
fn parses_parameter_modifier_function_kind() {
    let program = parse(
        "let identity = { <m: <p: parameters>: parameters, t: type>(m value: t): t =>  value }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(function.compile_groups[0][0].kind, Sort::ParameterModifier);
    assert_eq!(function.groups[0][0].modifiers, ["m"]);
}

#[test]
fn parses_effect_parameters_in_with_clauses() {
    let program = parse(
        "let tagged = { <e: effects>with<e>(value: i32): i32 =>  value }\n\
             let combined = { <e: effects>with<unsafety, e>(value: i32): i32 =>  value }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(function.compile_groups[0][0].kind, Sort::Effects);
    assert_eq!(function.effects.parameters, vec!["e"]);
    let Item::Function(combined) = &program.items[1] else {
        panic!("expected function");
    };
    assert_eq!(
        combined.effects.custom,
        vec![Type::Named("unsafety".to_owned(), Vec::new())]
    );
    assert_eq!(combined.effects.parameters, vec!["e"]);

    let error = parse("let box = <e: effects> struct { value: i32 }\n").unwrap_err();
    assert!(error
        .message
        .contains("effect parameters belong to functions"));

    let error = parse("let bad = { <e: effects>with<e>(value: e): i32 =>  0 }\n").unwrap_err();
    assert!(error.message.contains("cannot be used as a runtime type"));

    let error = parse("let old = { <e: effects>(value: i32): i32(e) =>  value }\n").unwrap_err();
    assert!(!error.message.is_empty());
}

#[test]
fn parses_compiler_owned_constraint_fragments_and_rejects_defaults() {
    let program = parse("let inspect = { <c: constraint>(): () =>  () }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        function.compile_groups[0][0].kind,
        Sort::Fragment(crate::ast::StaticFragmentKind::constraint())
    );

    let constraint = parse("let bad = { <c: constraint = value>(): () =>  () }\n").unwrap_err();
    assert!(constraint
        .message
        .contains("defaults for constraint fragments are not supported"));
    let runtime = parse("let bad = { <c: constraint>(value: c): () =>  () }\n").unwrap_err();
    assert!(
        runtime
            .message
            .contains("constraint fragment parameter `c` cannot be used as a runtime type"),
        "{}",
        runtime.message
    );

    let ordinary = parse("let inspect = { <d: declaration>(): () =>  () }\n").unwrap();
    let Item::Function(function) = &ordinary.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        function.compile_groups[0][0].kind,
        Sort::Named("declaration".into())
    );
}

#[test]
fn parses_trait_self_effect_parameter_in_member_rows() {
    let program = parse(
            "let Handle = trait<self: effect> {\n\
             Clauses: <Value: type, Answer: type>: parameters\n\
             handle: <Value: type, Answer: type, rest: effects>with<rest> ...Clauses<Value, Answer>{move action: with<self, rest>(): Value}: Answer\n\
             }\n",
        )
        .unwrap();
    let Item::Trait(definition) = &program.items[0] else {
        panic!("expected trait");
    };
    let TraitMember::AssociatedType { kind, .. } = &definition.members[0] else {
        panic!("expected clauses schema");
    };
    assert_eq!(*kind, AssociatedKind::Parameters);
    let TraitMember::Function(function) = &definition.members[1] else {
        panic!("expected handle member");
    };
    assert_eq!(
        function.groups[0][0].ty,
        Type::Named(
            "$parameter$groups$expand".to_owned(),
            vec![Type::Named(
                "Clauses".to_owned(),
                vec![
                    Type::Named("Value".to_owned(), Vec::new()),
                    Type::Named("Answer".to_owned(), Vec::new()),
                ],
            )],
        )
    );
    assert_eq!(function.effects.parameters, vec!["rest"]);
    let Type::Function { effects, .. } = &function.groups[1][0].ty else {
        panic!("expected action callable parameter");
    };
    assert_eq!(effects.parameters, vec!["rest", "self"]);
    assert!(effects.custom.is_empty());
}

#[test]
fn parses_compiler_provided_sort_and_control_contract_declarations() {
    let program = parse(
        "pub let unsafety = effect {}\n\
             pub let throwing = <error: type> effect { raise: (move error: error): never }\n\
             pub let type: sort<2>\n\
             pub let effect: sort<2>\n\
             pub let effects: sort<2>\n\
             pub let empty = sort<1>{}\n\
             pub let access = sort<1> {\n\
             /// shared read-only access.\n\
             shared\n\
             /// exclusive mutable access.\n\
             mut\n\
             }\n\
             pub let do = { <e: effects, t: type>with<e>(move action: with<e>(): t): t }\n",
    )
    .unwrap();
    assert!(matches!(
        &program.items[0],
        Item::Effect(effect) if effect.compile_groups.is_empty()
    ));
    assert!(matches!(
        &program.items[1],
        Item::Effect(effect) if effect.compile_groups.len() == 1 && effect.operations.len() == 1
    ));
    assert!(matches!(
        &program.items[2],
        Item::Sort(sort) if sort.name == "type" && sort.level == 2 && sort.members.is_none()
    ));
    assert!(matches!(
        &program.items[3],
        Item::Sort(sort) if sort.name == "effect" && sort.level == 2 && sort.members.is_none()
    ));
    assert!(matches!(
        &program.items[4],
        Item::Sort(sort) if sort.name == "effects" && sort.level == 2 && sort.members.is_none()
    ));
    assert!(matches!(
        &program.items[5],
        Item::Sort(sort)
            if sort.name == "empty" && sort.level == 1 && sort.members == Some(Vec::new())
    ));
    assert!(matches!(
        &program.items[6],
        Item::Sort(sort) if sort.name == "access"
            && sort.members.as_ref().is_some_and(|members| members.iter().map(String::as_str)
                .eq(["shared", "mut"])
            )
    ));
    assert!(matches!(
        &program.items[7],
        Item::Function(function) if function.name == "do" && function.body.is_none()
    ));
}

#[test]
fn parses_complete_builtin_definition_markers() {
    let program = parse(
        "let builtin = { (): never => builtin() }\n\
             pub let scalar: type = builtin()\n\
             pub let family = <t: type><l: usize>: type builtin()\n\
             pub let intrinsic = { <t: type>(value: t): t => builtin() }\n\
             extend(i32, Add<i32>) {\n\
             let Output = i32\n\
             let add = { (self)(rhs: i32): i32 => builtin() }\n\
             }\n",
    )
    .expect("builtin markers should parse as complete declaration initializers");

    assert!(matches!(
        &program.items[0],
        Item::Function(function)
            if function.name == "builtin"
                && function.builtin
                && function.body.is_none()
                && function.return_type == Some(Type::Named("never".to_owned(), Vec::new()))
    ));
    assert!(matches!(
        &program.items[1],
        Item::TypeForm(definition)
            if definition.name == "scalar" && definition.builtin
    ));
    assert!(matches!(
        &program.items[2],
        Item::TypeForm(definition)
            if definition.name == "family"
                && definition.builtin
                && definition.compile_groups.len() == 2
    ));
    assert!(matches!(
        &program.items[3],
        Item::Function(function)
            if function.name == "intrinsic" && function.builtin && function.body.is_none()
    ));
    let Item::Extend(extension) = &program.items[4] else {
        panic!("expected extension");
    };
    assert!(matches!(
        &extension.members[1],
        ExtendMember::Function(function) if function.builtin && function.body.is_none()
    ));
}

#[test]
fn rejects_malformed_builtin_definition_markers() {
    for source in [
        "let builtin = (): never builtin()\n",
        "let value: i32 = builtin()\n",
        "let intrinsic = (value: i32) builtin()\n",
        "let scalar: type = builtin(1)\n",
        "extend(i32) { let constant = builtin() }\n",
    ] {
        assert!(parse(source).is_err(), "{source}");
    }
}

#[test]
fn parses_variadic_match_control_contract() {
    let program = parse(
        "pub let match = { <\n\
             Input: type,\n\
             Output: type,\n\
             e: effects,\n\
             ...cases: parameters,\n\
             >with<e>\n\
             (move input: Input)\n\
             ...cases: Output }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected match function");
    };
    assert_eq!(
        function.compile_groups,
        vec![vec![
            CompileParam {
                name: "Input".to_owned(),
                kind: Sort::Type,
                default: None,
            },
            CompileParam {
                name: "Output".to_owned(),
                kind: Sort::Type,
                default: None,
            },
            CompileParam {
                name: "e".to_owned(),
                kind: Sort::Effects,
                default: None,
            },
            CompileParam {
                name: "cases".to_owned(),
                kind: Sort::ParameterPack,
                default: None,
            },
        ]]
    );
    assert!(matches!(
        function.groups.as_slice(),
        [input, cases]
            if input[0].name == "input"
                && cases[0].name == "cases"
                && cases[0].ty
                    == Type::Named(
                        "$parameter$groups$expand".to_owned(),
                        vec![Type::Named("cases".to_owned(), Vec::new())],
                    )
    ));
    assert_eq!(
        function.return_type,
        Some(Type::Named("Output".to_owned(), Vec::new()))
    );
    assert_eq!(function.effects.parameters, vec!["e"]);
    assert!(function.body.is_none());
}

#[test]
fn parses_type_forms_and_closed_enums_from_the_type_sort() {
    let program = parse("pub let i32: type\npub let bool = enum { false, true }\n").unwrap();
    assert!(matches!(
        &program.items[0],
        Item::TypeForm(definition)
            if definition.name == "i32"
                && definition.compile_groups.is_empty()
                && definition.values.is_empty()
    ));
    assert!(matches!(
        &program.items[1],
        Item::Enum(definition)
            if definition.name == "bool"
                && definition.compile_groups.is_empty()
                && definition.variants.iter().map(|variant| variant.name.as_str())
                    .eq(["false", "true"])
    ));
}

#[test]
fn rejects_removed_type_value_syntax_and_duplicate_enum_variants() {
    let error = parse("let i32 = type\n").unwrap_err();
    assert!(error.message.contains("abstract sort"));

    let error = parse("let bool = type { false, true }\n").unwrap_err();
    assert!(error.message.contains("abstract sort"));

    let error = parse("let kind = sort\n").unwrap_err();
    assert!(error.message.contains("`<` after `sort`"));

    let error = parse("let kind = sort<0> { value }\n").unwrap_err();
    assert!(error.message.contains("`sort<0>` is invalid"));

    let error = parse("let kind: sort\n").unwrap_err();
    assert!(error.message.contains("`<` after `sort`"));

    let error = parse("let bool = enum { false, false }\n").unwrap_err();
    assert!(error.message.contains("duplicate enum variant `false`"));
}

#[test]
fn parses_nominal_marker_effect_declarations_and_callable_rows() {
    let program = parse(
        "pub let ui = effect\n\
             let render = { with<ui>(): i32 =>  0 }\n\
             let invoke = { with<ui>(action: with<ui>(): i32): i32 =>  action() }\n",
    )
    .unwrap();

    assert!(matches!(&program.items[0], Item::Effect(effect) if effect.name == "ui"));
    let Item::Function(render) = &program.items[1] else {
        panic!("expected render function");
    };
    assert_eq!(
        render.effects.custom,
        [Type::Named("ui".into(), Vec::new())]
    );
    let Item::Function(invoke) = &program.items[2] else {
        panic!("expected invoke function");
    };
    assert!(matches!(
        &invoke.groups[0][0].ty,
        Type::Function { effects, .. }
            if effects.custom == [Type::Named("ui".into(), Vec::new())]
    ));

    let duplicate = parse("let f = { with<ui, ui>(): i32 =>  0 }\n").unwrap_err();
    assert!(duplicate.message.contains("duplicate custom effect `ui`"));

    parse("let local_effect = effect\n")
        .expect("snake_case effect declarations are valid nominal identities");
    parse("let f = { with<core.effect.ui>(): i32 =>  0 }\n")
        .expect("snake_case qualified effect names are valid");
}

#[test]
fn parses_parameterized_algebraic_effect_operations() {
    let program = parse(
        "let state = <s: type> effect {\n\
             get: (): s\n\
             put: (move value: s): ()\n\
             }\n\
             let program = { with<state<i32>>(): i32 =>  0 }\n",
    )
    .unwrap();
    let Item::Effect(state) = &program.items[0] else {
        panic!("expected state effect");
    };
    assert_eq!(state.compile_groups[0][0].name, "s");
    assert_eq!(state.operations.len(), 2);
    assert_eq!(state.operations[0].name, "get");
    assert_eq!(
        state.operations[0].return_type,
        Some(Type::Named("s".into(), Vec::new()))
    );
    assert_eq!(state.operations[1].groups[0][0].mode, PassMode::Move);

    let Item::Function(program) = &program.items[1] else {
        panic!("expected program function");
    };
    assert_eq!(
        program.effects.custom,
        [Type::Named("state".into(), vec![Type::I32])]
    );
}

#[test]
fn permits_effect_operation_overloads_only_by_parameter_names() {
    let program = parse(
        "let ask = effect {\n\
             value: (left: i32): i32\n\
             value: (right: i32): i32\n\
             }\n",
    )
    .expect("distinct operation labels should form an overload set");
    let Item::Effect(ask) = &program.items[0] else {
        panic!("expected ask effect");
    };
    assert_eq!(ask.operations.len(), 2);

    let duplicate = parse(
        "let ask = effect {\n\
             value: (input: i32): i32\n\
             value: (input: i64): i64\n\
             }\n",
    )
    .expect_err("types must not participate in operation overload selection");
    assert!(duplicate.message.contains("same parameter names"));
}

#[test]
fn parses_function_shaped_handlers_with_contextual_clause_parameters() {
    let program = parse(
        "let state = <s: type> effect { get: (): s }\n\
             let main = { (): i32 => \n\
             state<i32>.handle(state<i32>.get()) {\n\
             get(resume) => resume(42)\n\
             }\n\
             }\n",
    )
    .unwrap();
    let Item::Function(main) = &program.items[1] else {
        panic!("expected main function");
    };
    let Expr::DelimitedCall {
        delimiter,
        arguments,
        ..
    } = function_tail(main)
    else {
        panic!("expected brace-delimited handler call");
    };
    assert_eq!(*delimiter, GroupDelimiter::Brace);
    assert_eq!(arguments.len(), 2);
}

#[test]
fn parses_effects_as_part_of_callable_signatures() {
    let program = parse(
            "let apply = { <e: effects>with<e>(action: with<e>(i32): i32)(value: i32): i32 =>  value }\n",
        )
        .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(matches!(
        &function.groups[0][0].ty,
        Type::Function { groups, effects, result }
            if groups == &vec![vec![Type::I32]]
                && effects.parameters == vec!["e"]
                && result.as_ref() == &Type::I32
    ));

    let old =
        parse("let apply = { <e: effects>(action: (i32) {i32(e}))(value: i32): i32 =>  value }\n")
            .unwrap_err();
    assert!(!old.message.is_empty());
}

#[test]
fn rejects_parameter_modifier_parameters_on_data_declarations() {
    let error = parse("let wrapper = <m: <p: parameters>: parameters> struct { value: i32 }\n")
        .unwrap_err();
    assert!(error
        .message
        .contains("modifier parameters belong to functions"));
}

#[test]
fn rejects_undeclared_access_parameters() {
    let error = parse("let invalid = { (value: Borrow<a><i32>): i32 =>  value }\n").unwrap_err();
    assert!(error
        .message
        .contains("undeclared access or region parameter `a`"));
}

#[test]
fn rejects_the_legacy_while_condition_form() {
    let error = parse("let main = { (): () =>  while ready() { work() } }\n").unwrap_err();
    assert!(error
        .message
        .contains("`while` requires `while(condition) { ... }`"));
}

#[test]
fn rejects_every_removed_if_while_and_do_alias() {
    for source in [
        "let main = { (): i32 =>  if true { 1 } else: { 0 } }\n",
        "let main = { (): i32 =>  if(true) then: { 1 } else: { 0 } }\n",
        "let main = { (): i32 =>  if true { 1 } { 0 } }\n",
        "let main = { (): i32 =>  if true then: { 1 } else: { 0 } }\n",
        "let main = { (): () =>  while { ready() } { work() } }\n",
        "let main = { (): () =>  while condition: { ready() } do: { work() } }\n",
        "let main = { (): () =>  do { work() } while { ready() } }\n",
    ] {
        assert!(
            parse(source).is_err(),
            "removed control-flow alias parsed: {source}"
        );
    }
}

#[test]
fn rejects_parenthesized_trait_and_extension_requires_groups() {
    for source in [
        "let marker = trait {}\nlet bounded = trait(requires: self is marker) {}\n",
        "let marker = trait {}\nlet cell = struct {}\nextend(cell)(requires: cell is marker) {}\n",
    ] {
        assert!(
            parse(source).is_err(),
            "parenthesized requirements parsed: {source}"
        );
    }
}

#[test]
fn parses_canonical_while() {
    let program = parse("let main = { (): () =>  while(Ready()) { work() } }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(matches!(
        function_tail(function),
        Expr::While { condition, body, post_test: false }
            if matches!(condition.as_ref(), Expr::Call(_, _))
                && matches!(body.as_ref(), Expr::Block(_, _))
    ));
}

#[test]
fn parses_do_while_as_the_labeled_do_overload() {
    let program =
        parse("let main = { (): () => \n  do { work() } while: { Ready() }\n}\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert!(matches!(
        function_tail(function),
        Expr::While {
            condition,
            body,
            post_test: true,
        } if matches!(condition.as_ref(), Expr::Block(_, _))
            && matches!(body.as_ref(), Expr::Block(_, _))
    ));
}

#[test]
fn parses_canonical_if() {
    let program =
        parse("let main = { (): i32 =>  if(false) { 0 } else: { if(true) { 42 } else: { 0 } } }\n")
            .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let (_, then_branch, else_branch) = if_call_parts(function_tail(function));
    assert!(matches!(then_branch, Expr::Block(_, _)));
    assert!(matches!(else_branch, Expr::Block(_, _)));
}

#[test]
fn rejects_removed_while_let_syntax() {
    let error =
        parse("let main = { (): () =>  while let some(value) = next() { consume(value) } }\n")
            .unwrap_err();
    assert!(error
        .message
        .contains("`while` requires `while(condition) { ... }`"));
}

#[test]
fn parses_loop_with_break_value() {
    let program = parse("let main = { (): i32 =>  loop {\n  break(40 + 2)\n} }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Expr::Loop { body } = function_tail(function) else {
        panic!("expected loop");
    };
    assert!(matches!(
        body.as_ref(),
        Expr::Block(_, Some(tail))
            if matches!(
                tail.unlocated(),
                Expr::Break(Some(value))
                    if matches!(value.as_ref(), Expr::Binary(_, BinaryOp::Add, _))
            )
    ));
}

#[test]
fn desugars_for_to_iteration_lang_item_calls() {
    let program =
        parse("let main = { (): () => for (values()) { value -> consume(value) } }\n").unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Expr::Block(statements, None) = function_tail(function) else {
        panic!("expected desugared for block");
    };
    assert!(matches!(
        &statements[0],
        Stmt::Let(Binding { mutable: true, value: Expr::Call(callee, _), .. })
            if matches!(callee.as_ref(), Expr::Member(_, member) if member == "$lang$into_iter")
    ));
    assert!(matches!(
        &statements[1],
        Stmt::Let(Binding {
            annotation: Some(Type::Unit),
            value: Expr::Loop { .. },
            ..
        })
    ));
}

#[test]
fn rejects_bare_control_exits() {
    for (source, expected) in [
        (
            "let run = { (): () =>  loop { break } }\n",
            "use `break()` or `break(value)`",
        ),
        (
            "let run = { (): () =>  loop { continue } }\n",
            "`(` after `continue`",
        ),
        (
            "let run = { (): () =>  return }\n",
            "use `return()` or `return(value)`",
        ),
        (
            "let run = { (): () =>  async { await value } }\n",
            "`await` requires `await(value)`",
        ),
    ] {
        let error = parse(source).unwrap_err();
        assert!(error.message.contains(expected), "{}", error.message);
    }
}

#[test]
fn array_length_must_be_a_restricted_static_expression() {
    let error =
        parse("let main = { (values: Array<i32><borrow(value)>): i32 =>  0 }\n").unwrap_err();
    assert!(
        error.message.contains("invalid compile-time array length"),
        "{}",
        error.message
    );
    assert!(
        error.message.contains("expected a pure expression"),
        "{}",
        error.message
    );
}

#[test]
fn array_type_preserves_curried_compile_parameter_groups() {
    let program = parse(
        "pub let Array = <T: type><l: usize>: type\n\
             let first = { <l: usize>(values: Array<i32><l>): i32 =>  values[0] }\n",
    )
    .unwrap();

    let Item::TypeForm(array) = &program.items[0] else {
        panic!("expected array type form");
    };
    assert_eq!(
        array.compile_groups,
        vec![
            vec![CompileParam {
                name: "T".to_owned(),
                kind: Sort::Type,
                default: None,
            }],
            vec![CompileParam {
                name: "l".to_owned(),
                kind: Sort::USize,
                default: None,
            }]
        ]
    );

    let concrete = parse("let values = : Array<i32><2> [1, 2]\n");
    assert!(concrete.is_ok(), "{concrete:?}");

    let error = parse("let values = : Array<i32, 2> [1, 2]\n").unwrap_err();
    assert!(error.message.contains("second group"), "{}", error.message);
}

#[test]
fn parses_extend_methods_associated_functions_constants_and_trait_refs() {
    let program = parse(
        "let a = struct { value: i32 }\n\
             extend(a, foo) {\n\
             let reset = { (self: Borrow<mut><self>)(): () => }\n\
             let answer: i32 = 42\n\
             let make = { (value: i32): a =>  a{ value: value } }\n\
             }\n",
    )
    .unwrap();

    let Item::Extend(extension) = &program.items[1] else {
        panic!("expected extend declaration");
    };
    assert_eq!(extension.target, Type::Named("a".into(), Vec::new()));
    assert_eq!(
        extension.trait_ref,
        Some(Type::Named("foo".into(), Vec::new()))
    );
    assert_eq!(extension.members.len(), 3);

    let ExtendMember::Function(reset) = &extension.members[0] else {
        panic!("expected method");
    };
    assert_eq!(reset.name, "reset");
    assert_eq!(reset.groups.len(), 2);
    assert_eq!(reset.groups[0].len(), 1);
    assert_eq!(reset.groups[0][0].name, "self");
    assert_eq!(reset.groups[0][0].mode, PassMode::Inferred);
    assert_eq!(
        reset.groups[0][0].ty,
        Type::Borrow {
            mutable: true,
            access: None,
            region: None,
            pointee: Box::new(Type::Named("self".into(), Vec::new())),
        }
    );
    assert!(reset.groups[1].is_empty());

    let ExtendMember::Const(answer) = &extension.members[1] else {
        panic!("expected associated constant");
    };
    assert_eq!(answer.name, "answer");
    assert_eq!(answer.annotation, Some(Type::I32));

    let ExtendMember::Function(make) = &extension.members[2] else {
        panic!("expected associated function");
    };
    assert_eq!(make.name, "make");
    assert!(make
        .groups
        .iter()
        .flatten()
        .all(|param| param.name != "self"));
}

#[test]
fn parses_compile_parameters_on_extend_functions() {
    let program = parse(
        "extend(a) {\n\
             let convert = { <t: type>(self: Borrow<self>)(value: t): t =>  value }\n\
             let make = { <t: type>(value: t): t =>  value }\n\
             }\n",
    )
    .unwrap();

    let Item::Extend(extension) = &program.items[0] else {
        panic!("expected extend declaration");
    };
    let ExtendMember::Function(convert) = &extension.members[0] else {
        panic!("expected generic method");
    };
    assert_eq!(convert.compile_groups[0][0].name, "t");
    assert_eq!(convert.groups.len(), 2);
    assert_eq!(convert.groups[0][0].name, "self");
    assert_eq!(convert.groups[1][0].ty, Type::Named("t".into(), Vec::new()));

    let ExtendMember::Function(make) = &extension.members[1] else {
        panic!("expected generic associated function");
    };
    assert_eq!(make.compile_groups.len(), 1);
    assert_eq!(make.groups.len(), 1);
}

#[test]
fn infers_extend_pattern_parameters_from_constructor_sorts() {
    let program = parse(
        "let cell = <t: type> struct { value: t }\n\
             extend(cell<t>)<requires: t is Copyable> {\n\
             let get = { (self: Borrow<self>)(): t =>  self.value }\n}\n",
    )
    .unwrap();

    let Item::Extend(extension) = &program.items[1] else {
        panic!("expected extend declaration");
    };
    assert_eq!(extension.compile_groups.len(), 1);
    assert_eq!(extension.where_predicates.len(), 1);
    assert_eq!(extension.compile_groups[0][0].name, "t");
    assert_eq!(
        extension.target,
        Type::Named("cell".into(), vec![Type::Named("t".into(), Vec::new())])
    );

    let program = parse(
        "let Result = <Error: type><T: type> enum { Ok(T), Err(Error) }\n\
             let Chain = trait {}\n\
             extend(Result<Error><T>, Chain) {}\n",
    )
    .unwrap();
    let Item::Extend(extension) = &program.items[2] else {
        panic!("expected destructuring extend declaration");
    };
    assert_eq!(
        extension.compile_groups[0]
            .iter()
            .map(|parameter| (parameter.name.as_str(), parameter.kind.clone()))
            .collect::<Vec<_>>(),
        vec![("Error", Sort::Type), ("T", Sort::Type)]
    );
    assert_eq!(
        extension.trait_ref,
        Some(Type::Named("Chain".into(), Vec::new()))
    );
}

#[test]
fn qualified_extend_roots_are_not_inferred_as_parameters() {
    let program = parse(
        "let Functor = trait<self: <Value: type>: type> {}\n\
             extend(core.option.Option, Functor) {}\n\
             extend(core.result.Result<Error>, Functor) {}\n",
    )
    .unwrap();
    let Item::Extend(option) = &program.items[1] else {
        panic!("expected qualified option extension");
    };
    assert!(option.compile_groups.is_empty());
    let Item::Extend(result) = &program.items[2] else {
        panic!("expected qualified result extension");
    };
    assert_eq!(result.compile_groups[0][0].name, "Error");
    assert_eq!(result.compile_groups[0][0].kind, Sort::Type);
}

#[test]
fn parses_multiline_constraint_guards_without_inference_placeholders() {
    let program = parse(
        "let choose = { <t: type>(copy value: t): t\n\
             requires(t is Copyable && t is marker<i32> && t.item == t) => value }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected a generic function");
    };
    assert_eq!(function.where_predicates.len(), 2);
    assert_eq!(
        function.where_predicates[0].subject,
        Type::Named("t".into(), Vec::new())
    );
    assert_eq!(
        function.where_predicates[1].trait_ref,
        Type::Named("marker".into(), vec![Type::I32])
    );
    assert_eq!(function.where_predicates[1].associated_types.len(), 1);
    assert_eq!(
        function.where_predicates[1].associated_types[0].name,
        "item"
    );
    assert_eq!(
        function.where_predicates[1].associated_types[0].ty,
        Type::Named("t".into(), Vec::new())
    );
}

#[test]
fn lowers_compile_time_constraint_guards_to_trait_predicates() {
    let program = parse(
        "let Copyable = trait {}\n\
             let cell = <t: type> struct { value: t }\n\
             let duplicate = { <t: type>(value: t): (t, t) requires(t is Copyable) => \n\
             (value, value)\n\
             }\n\
             extend(cell<t>, Copyable)<requires: t is Copyable> {}\n",
    )
    .unwrap();

    let Item::Function(function) = &program.items[2] else {
        panic!("expected guarded function");
    };
    assert_eq!(function.where_predicates.len(), 1);
    assert_eq!(
        function.where_predicates[0].subject,
        Type::Named("t".into(), Vec::new())
    );
    assert_eq!(
        function.where_predicates[0].trait_ref,
        Type::Named("Copyable".into(), Vec::new())
    );

    let Item::Extend(extension) = &program.items[3] else {
        panic!("expected guarded extension");
    };
    assert_eq!(extension.where_predicates, function.where_predicates);
}

#[test]
fn constraint_guards_require_is_evidence_before_projection_equalities() {
    let error = parse("let read = { <t: type>(value: t): t requires(t.item == i32) =>  value }\n")
        .expect_err("a projection without trait evidence must fail");
    assert!(error
        .message
        .contains("must follow an `is` constraint for the same subject"));

    let error = parse("let read = { <t: type>(value: t): t where t: Copyable =>  value }\n")
        .expect_err("colon-style predicates must fail");
    assert!(error
        .message
        .contains("`=>` before callable implementation"));
}

#[test]
fn parses_generic_associated_type_equalities() {
    let program = parse(
            "let lend = { <t: type>(value: t): t\n\
             requires(t is lender && t.item<a: access><r: region> == Borrow<a><r><i32>) => value }\n",
        )
        .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected a generic function");
    };
    let binding = &function.where_predicates[0].associated_types[0];
    assert_eq!(binding.name, "item");
    assert_eq!(binding.compile_groups.len(), 2);
    assert!(binding.compile_groups[0][0].kind.is_access());
    assert_eq!(binding.compile_groups[1][0].kind, Sort::Region);
    assert_eq!(
        binding.ty,
        Type::Borrow {
            mutable: false,
            access: Some("a".into()),
            region: Some("r".into()),
            pointee: Box::new(Type::I32),
        }
    );
}

#[test]
fn rejects_invalid_extend_receivers() {
    let cases = [
        (
            "extend(a) { let invalid = { (self, value: i32)(): () => } }\n",
            "only parameter",
        ),
        (
            "extend(a) { let invalid = { (self): () => } }\n",
            "requires an explicit parameter group",
        ),
        (
            "extend(a) { let invalid = { (value: i32)(self)(): () => } }\n",
            "first parameter group",
        ),
        (
            "extend(a) { let invalid = { (self)(self)(): () => } }\n",
            "at most one",
        ),
    ];

    for (source, expected) in cases {
        let error = parse(source).unwrap_err();
        assert!(
            error.message.contains(expected),
            "expected `{expected}` in `{}`",
            error.message
        );
    }
}

#[test]
fn parses_borrow_and_move_receivers_with_explicit_following_groups() {
    let program = parse(
        "extend(a) {\n\
             let inspect = { (self: Borrow<self>)(): i32 =>  self.value }\n\
             let replace = { (move self)(value: i32)(other: i32): a =>  a{ value: value + other } }\n\
             }\n",
    )
    .unwrap();

    let Item::Extend(extension) = &program.items[0] else {
        panic!("expected extend declaration");
    };
    let ExtendMember::Function(inspect) = &extension.members[0] else {
        panic!("expected method");
    };
    assert_eq!(inspect.groups[0][0].mode, PassMode::Inferred);
    assert_eq!(
        inspect.groups[0][0].ty,
        Type::Borrow {
            mutable: false,
            access: None,
            region: None,
            pointee: Box::new(Type::Named("self".into(), Vec::new())),
        }
    );
    assert!(inspect.groups[1].is_empty());

    let ExtendMember::Function(replace) = &extension.members[1] else {
        panic!("expected method");
    };
    assert_eq!(replace.groups[0][0].mode, PassMode::Move);
    assert_eq!(replace.groups.len(), 3);
}

#[test]
fn rejects_receivers_outside_extend_and_invalid_extend_members() {
    let receiver = parse("let invalid = { (self: a)(): () => }\n").unwrap_err();
    assert!(receiver.message.contains("only allowed in extend"));

    let mutable = parse("extend(a) { let mut answer = 42 }\n").unwrap_err();
    assert!(mutable.message.contains("let mut"));

    let data = parse("extend(a) { let nested = struct { value: i32 } }\n").unwrap_err();
    assert!(data.message.contains("data declarations"));

    let missing = parse("extend(a) { let answer: i32\n}\n").unwrap_err();
    assert!(missing.message.contains("expected `=`"));
}

#[test]
fn reports_a_source_location() {
    let error = parse("let main = { (): i32 => \n  let x =\n}\n").unwrap_err();
    assert_eq!((error.line, error.column), (3, 1));
    assert!(error.message.contains("expression"));
}

#[test]
fn parses_type_families_and_type_constructor_aliases() {
    let program = parse(
        "let family = <t: type>: type box<t>;\n\
             let constructor: <element: type>: type = box;\n\
             let scalar: type = i32\n",
    )
    .unwrap();

    let Item::TypeAlias(family) = &program.items[0] else {
        panic!("expected type-family alias");
    };
    assert_eq!(family.compile_groups[0][0].name, "t");
    assert_eq!(
        family.target,
        Type::Named("box".into(), vec![Type::Named("t".into(), Vec::new())])
    );

    let Item::TypeAlias(constructor) = &program.items[1] else {
        panic!("expected type-constructor alias");
    };
    assert_eq!(constructor.compile_groups[0][0].name, "element");
    assert_eq!(
        constructor.target,
        Type::Named(
            "box".into(),
            vec![Type::Named("element".into(), Vec::new())]
        )
    );

    assert!(matches!(
        &program.items[2],
        Item::TypeAlias(alias) if alias.compile_groups.is_empty() && alias.target == Type::I32
    ));
}

#[test]
fn parses_constructor_compile_parameter_sorts() {
    let program = parse(
            "let use = { <f: <element: type>: type>(move value: f<i32>): f<i32> =>  value }\n\
             let curried = { <f: <element: type><length: usize>: type>(): i32 =>  0 }\n\
              let effects = { <e: <error: type>: effect>with<e<bool>>(move action: with<e<bool>>(): i32): i32 =>  action() }\n\
              let functor = trait<self: <value: type>: type> {\n\
             map: <e: effects, a: type, b: type>with<e>(move self: self<a>)(move transform: with<e>(a): b): self<b>\n\
             }\n\
             let applicative = trait<self: <value: type>: type><requires: self is functor> {\n\
             pure: <a: type>(move value: a): self<a>\n}\n",
        )
        .unwrap();

    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        function.compile_groups[0][0].kind,
        Sort::TypeConstructor {
            parameter_groups: vec![vec![Sort::Type]],
        }
    );
    assert_eq!(
        function.groups[0][0].ty,
        Type::Named("f".into(), vec![Type::I32])
    );

    let Item::Function(curried) = &program.items[1] else {
        panic!("expected curried constructor function");
    };
    assert_eq!(
        curried.compile_groups[0][0].kind,
        Sort::TypeConstructor {
            parameter_groups: vec![vec![Sort::Type], vec![Sort::USize]],
        }
    );

    let Item::Function(effects) = &program.items[2] else {
        panic!("expected effect-constructor function");
    };
    assert_eq!(
        effects.compile_groups[0][0].kind,
        Sort::EffectConstructor {
            parameter_groups: vec![vec![Sort::Type]],
        }
    );
    assert_eq!(
        effects.effects.custom,
        vec![Type::Named("e".into(), vec![Type::Bool])]
    );

    let Item::Trait(trait_def) = &program.items[3] else {
        panic!("expected trait");
    };
    assert_eq!(
        trait_def.self_parameter.kind,
        Sort::TypeConstructor {
            parameter_groups: vec![vec![Sort::Type]],
        }
    );
    assert!(trait_def.compile_groups.is_empty());

    let Item::Trait(applicative) = &program.items[4] else {
        panic!("expected inherited trait");
    };
    assert_eq!(applicative.where_predicates.len(), 1);
    assert_eq!(
        applicative.where_predicates[0].subject,
        Type::Named("self".into(), Vec::new())
    );
    assert_eq!(
        applicative.where_predicates[0].trait_ref,
        Type::Named("functor".into(), Vec::new())
    );
}

#[test]
fn parses_labeled_type_arguments_without_reordering() {
    let program = parse(
        "let consume = { (value: pair<v: bool, k: i32>): Result<e: bool><t: i32> =>  value }\n",
    )
    .unwrap();
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    assert_eq!(
        function.groups[0][0].ty,
        Type::NamedArgs(
            "pair".into(),
            vec![
                TypeArg {
                    label: Some("v".into()),
                    ty: Type::Bool,
                },
                TypeArg {
                    label: Some("k".into()),
                    ty: Type::I32,
                },
            ],
        )
    );
    assert_eq!(
        function.return_type,
        Some(Type::NamedArgs(
            "Result".into(),
            vec![
                TypeArg {
                    label: Some("e".into()),
                    ty: Type::Bool,
                },
                TypeArg {
                    label: Some("t".into()),
                    ty: Type::I32,
                },
            ],
        ))
    );
}

#[test]
fn parses_optional_fields_and_complete_method_groups() {
    let program = parse(
        "let field = value?.answer\n\
             let called = value?.convert(1)(2)\n",
    )
    .unwrap();
    let Item::Global(field) = &program.items[0] else {
        panic!("expected field binding");
    };
    assert!(matches!(
        &field.value,
        Expr::ChainMember(base, member)
            if matches!(base.as_ref(), Expr::Name(name) if name == "value")
                && member == "answer"
    ));
    let Item::Global(called) = &program.items[1] else {
        panic!("expected call binding");
    };
    let Expr::Call(first, second_group) = &called.value else {
        panic!("expected second call group");
    };
    let Expr::Call(root, first_group) = first.as_ref() else {
        panic!("expected first call group");
    };
    assert!(matches!(
        root.as_ref(),
        Expr::ChainMember(base, member)
            if matches!(base.as_ref(), Expr::Name(name) if name == "value")
                && member == "convert"
    ));
    assert_eq!(first_group.len(), 1);
    assert_eq!(second_group.len(), 1);
}

#[test]
fn records_local_initializer_and_statement_ranges() {
    let program = parse(
        "let main = { (): i32 => \n  let value: i32 = true\n  value\n}\n\
             let choose = { (): i32 =>  if(true) { 1 } else: { 2 } }\n",
    )
    .expect("parse source ranges");
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, Some(tail))) = &function.body else {
        panic!("expected function block");
    };
    let Stmt::Let(binding) = &statements[0] else {
        panic!("expected local binding");
    };
    assert_eq!(
        binding.value_source.as_deref(),
        Some(&crate::ast::SourceSpan {
            line: 2,
            column: 20,
            end_line: 2,
            end_column: 24,
        })
    );
    assert!(matches!(
        tail.as_ref(),
        Expr::Located {
            line: 3,
            column: 3,
            end_line: 3,
            end_column: 8,
            ..
        }
    ));
    let Item::Function(function) = &program.items[1] else {
        panic!("expected brace-body function");
    };
    let Some(Expr::Block(_, Some(tail))) = &function.body else {
        panic!("expected brace-body block");
    };
    assert!(matches!(
        tail.as_ref(),
        Expr::Located { value, .. }
            if matches!(value.as_ref(), Expr::DelimitedCall { delimiter: GroupDelimiter::Brace, arguments, .. }
                if arguments.iter().any(|argument| {
                    matches!(argument.value, Expr::Closure(_, _))
                }))
    ));
}

#[test]
fn parses_contextual_async_and_await_as_language_expressions() {
    let program =
        parse("let make = { (): i32 => \n  let future = async { await(next()) }\n  0\n}\n")
            .expect("async expressions must parse");
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, _)) = &function.body else {
        panic!("expected function block");
    };
    let Stmt::Let(binding) = &statements[0] else {
        panic!("expected future binding");
    };
    let Expr::Async { body } = binding.value.unlocated() else {
        panic!("expected async expression");
    };
    let Expr::Block(_, Some(tail)) = body.as_ref() else {
        panic!("expected async body");
    };
    assert!(matches!(tail.unlocated(), Expr::Await(_)));

    let program = parse(
            "let make = { (): i32 => \n  let future = async {\n    while(true) {\n      let value = await(next());\n      break()\n    }\n  }\n  0\n}\n",
        )
        .expect("control-flow blocks must preserve their enclosing async context");
    let Item::Function(function) = &program.items[0] else {
        panic!("expected function");
    };
    let Some(Expr::Block(statements, _)) = &function.body else {
        panic!("expected function block");
    };
    let Stmt::Let(binding) = &statements[0] else {
        panic!("expected future binding");
    };
    let Expr::Async { body } = binding.value.unlocated() else {
        panic!("expected async expression");
    };
    let Expr::Block(_, Some(tail)) = body.as_ref() else {
        panic!("expected async body");
    };
    let Expr::While { body, .. } = tail.unlocated() else {
        panic!("expected while expression");
    };
    let Expr::Block(statements, _) = body.as_ref() else {
        panic!("expected while body");
    };
    let Stmt::Let(binding) = &statements[0] else {
        panic!("expected awaited binding");
    };
    assert!(matches!(binding.value.unlocated(), Expr::Await(_)));
}

#[test]
fn async_and_await_remain_contextual_identifiers() {
    parse(
        "let async: i32 = 1\n\
             let await = { (value: i32): i32 =>  value }\n\
             let main = { (): i32 =>  await(async) }\n",
    )
    .expect("contextual async spellings must remain ordinary identifiers");

    parse("let main = { (): i32 =>  async { { await(value) } } }\n")
        .expect("await accepts a delimited operand");
}

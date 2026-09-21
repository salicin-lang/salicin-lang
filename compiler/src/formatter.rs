use std::collections::HashSet;

use crate::lexer::{lex, TokenKind};
use crate::parser::{parse, parse_with_source_layout, SourceLayout};

/// Format one complete Salicin source while preserving its logical token
/// stream. Existing physical line breaks are retained because they delimit
/// expressions and trailing groups; nested block boundaries may add lines.
pub fn format_source(source: &str) -> Result<String, String> {
    let normalized = source.replace("\r\n", "\n").replace('\r', "\n");
    if normalized.is_empty() {
        return Ok(normalized);
    }

    let (_, source_layout) =
        parse_with_source_layout(&normalized).map_err(|error| error.to_string())?;
    let canonical = space_brace_groups(&normalized, &source_layout);
    let (_, source_layout) = parse_with_source_layout(&canonical).map_err(|error| {
        format!("internal formatter error: canonical source no longer parses: {error}")
    })?;
    let expanded = expand_nested_blocks(&canonical, &source_layout);
    let (_, source_layout) = parse_with_source_layout(&expanded).map_err(|error| {
        format!("internal formatter error: expanded source no longer parses: {error}")
    })?;
    let expanded = expand_multiline_match_arms(&expanded, &source_layout);
    let (_, source_layout) = parse_with_source_layout(&expanded).map_err(|error| {
        format!("internal formatter error: expanded match no longer parses: {error}")
    })?;
    let expanded = expand_multiline_callable_groups(&expanded, &source_layout);
    let (_, source_layout) = parse_with_source_layout(&expanded).map_err(|error| {
        format!("internal formatter error: expanded callable header no longer parses: {error}")
    })?;
    let layout = analyze_layout(&expanded, &source_layout)?;
    let mut state = ScanState::default();
    let mut output = String::with_capacity(expanded.len() + 1);
    for (line_index, line) in expanded.lines().enumerate() {
        let content = line.trim_end_matches([' ', '\t']);
        if content.trim().is_empty() {
            output.push('\n');
            continue;
        }

        let content = content.trim_start_matches([' ', '\t']);
        let analysis = state.scan_line(content);
        let syntax = &layout[line_index];
        let continuation = syntax.continuation;
        let indent = state
            .brace_depth
            .saturating_sub(usize::from(analysis.starts_with_close))
            .saturating_add(syntax.delimiter_indent)
            .saturating_add(continuation);
        output.push_str(&"  ".repeat(indent));
        output.push_str(content);
        output.push('\n');
        state.brace_depth = state
            .brace_depth
            .saturating_add(analysis.opens)
            .saturating_sub(analysis.closes);
    }

    let before = semantic_token_kinds(&expanded)?;
    let after = semantic_token_kinds(&output)?;
    if before != after {
        return Err("internal formatter error: formatting changed the logical token stream".into());
    }
    parse(&output).map_err(|error| {
        format!("internal formatter error: formatted source no longer parses: {error}")
    })?;
    Ok(output)
}

fn space_brace_groups(source: &str, layout: &SourceLayout) -> String {
    let mut output = source.to_owned();
    let tokens = lex(source).unwrap_or_default();
    for byte in layout.brace_groups.iter().copied().rev() {
        let Some(index) = tokens.iter().position(|token| token.start_byte == byte) else {
            continue;
        };
        let Some(previous) = index.checked_sub(1).and_then(|index| tokens.get(index)) else {
            continue;
        };
        let gap = &source[previous.end_byte..byte];
        if gap.chars().all(char::is_whitespace) {
            output.replace_range(previous.end_byte..byte, " ");
        } else if let Some(comment_end) = gap.rfind("*/").map(|offset| offset + 2) {
            let trailing = &gap[comment_end..];
            if !gap[..comment_end].contains('\n') && trailing.chars().all(char::is_whitespace) {
                output.replace_range(previous.end_byte + comment_end..byte, " ");
            }
        }
    }
    output
}

fn expand_nested_blocks(source: &str, layout: &SourceLayout) -> String {
    let mut regions = layout
        .blocks
        .iter()
        .chain(&layout.closures)
        .collect::<Vec<_>>();
    regions.sort_by_key(|region| region.open_byte);
    let mut insertions = Vec::new();
    for (parent_index, parent) in regions.iter().enumerate() {
        for child in &regions[parent_index + 1..] {
            if child.open_byte >= parent.close_byte {
                break;
            }
            if child.close_byte < parent.close_byte && child.open_line == parent.open_line {
                insertions.push(parent.body_start_byte);
            }
            if child.close_byte < parent.close_byte && child.close_line == parent.close_line {
                insertions.push(parent.close_byte);
            }
        }
    }

    let tokens = lex(source).expect("a parsed source must lex");
    let mut current_line = 0usize;
    let mut first_code_on_line = true;
    for (index, token) in tokens.iter().enumerate() {
        if token.line != current_line {
            current_line = token.line;
            first_code_on_line = true;
        }
        if token.kind == TokenKind::Newline {
            continue;
        }
        if token.kind == TokenKind::Eof {
            break;
        }
        if first_code_on_line && token.kind == TokenKind::RBrace {
            let mut previous = token;
            for close in &tokens[index + 1..] {
                if close.line != token.line
                    || close.kind != TokenKind::RBrace
                    || !source[previous.end_byte..close.start_byte]
                        .chars()
                        .all(char::is_whitespace)
                {
                    break;
                }
                insertions.push(close.start_byte);
                previous = close;
            }
        }
        first_code_on_line = false;
    }

    insertions.sort_unstable();
    insertions.dedup();
    let mut expanded = source.to_owned();
    for byte in insertions.into_iter().rev() {
        expanded.insert(byte, '\n');
    }
    expanded
}

fn expand_multiline_callable_groups(source: &str, layout: &SourceLayout) -> String {
    let tokens = lex(source).expect("a parsed source must lex");
    let mut insertions = Vec::new();

    for callable in &layout.blocks {
        if callable.open_line == callable.close_line {
            continue;
        }
        let header_tokens = tokens
            .iter()
            .filter(|token| {
                token.start_byte > callable.open_byte && token.start_byte < callable.close_byte
            })
            .filter(|token| token.kind != TokenKind::Newline)
            .collect::<Vec<_>>();
        let first = header_tokens.first().map(|token| &token.kind);
        if !matches!(
            first,
            Some(TokenKind::LParen | TokenKind::LBracket | TokenKind::LBrace | TokenKind::Less)
        ) && !matches!(first, Some(TokenKind::Ident(name)) if name == "with")
        {
            continue;
        }
        let mut depth = 0usize;
        let mut groups = Vec::new();
        let mut collect_groups = true;
        let mut found_arrow = false;
        for token in header_tokens {
            match token.kind {
                TokenKind::FatArrow if depth == 0 => {
                    found_arrow = true;
                    break;
                }
                TokenKind::Colon if depth == 0 => collect_groups = false,
                TokenKind::LParen | TokenKind::LBracket | TokenKind::LBrace => {
                    if collect_groups && depth == 0 {
                        groups.push(token);
                    }
                    depth += 1;
                }
                TokenKind::Less => depth += 1,
                TokenKind::RParen
                | TokenKind::RBracket
                | TokenKind::RBrace
                | TokenKind::Greater => depth = depth.saturating_sub(1),
                _ => {}
            }
        }
        if !found_arrow {
            continue;
        }
        for group in groups {
            let Some(previous) = tokens.iter().rev().find(|token| {
                token.end_byte <= group.start_byte && token.kind != TokenKind::Newline
            }) else {
                continue;
            };
            if !source[previous.end_byte..group.start_byte].contains('\n') {
                insertions.push(group.start_byte);
            }
        }
    }

    insertions.sort_unstable();
    insertions.dedup();
    let mut expanded = source.to_owned();
    for byte in insertions.into_iter().rev() {
        expanded.insert(byte, '\n');
    }
    expanded
}

fn expand_multiline_match_arms(source: &str, layout: &SourceLayout) -> String {
    let tokens = lex(source).expect("a parsed source must lex");
    let mut insertions = Vec::new();
    for matched in &layout.matches {
        let region = &matched.region;
        if region.open_line == region.close_line {
            continue;
        }
        for byte in matched.arms.iter().copied() {
            let Some(previous) = tokens.iter().rev().find(|token| {
                token.end_byte <= byte && token.kind != TokenKind::Newline
            }) else {
                continue;
            };
            if !source[previous.end_byte..byte].contains('\n') {
                insertions.push(byte);
            }
        }
        let Some(previous) = tokens.iter().rev().find(|token| {
            token.end_byte <= region.close_byte && token.kind != TokenKind::Newline
        }) else {
            continue;
        };
        if !source[previous.end_byte..region.close_byte].contains('\n') {
            insertions.push(region.close_byte);
        }
    }

    insertions.sort_unstable();
    insertions.dedup();
    let mut expanded = source.to_owned();
    for byte in insertions.into_iter().rev() {
        expanded.insert(byte, '\n');
    }
    expanded
}

#[derive(Default)]
struct LineSyntax {
    first: Option<TokenKind>,
    first_byte: Option<usize>,
    delimiter_depth: usize,
    leading_delimiter_closes: usize,
    code_token_count: usize,
    has_parameter_group: bool,
    has_repeated_parameter_group: bool,
    is_parameter_group: bool,
    is_repeated_parameter_group: bool,
    is_where_predicate: bool,
    last: Option<TokenKind>,
    delimiter_indent: usize,
    brace_depth: usize,
    continuation: usize,
}

fn analyze_layout(source: &str, source_layout: &SourceLayout) -> Result<Vec<LineSyntax>, String> {
    let mut lines = (0..source.lines().count())
        .map(|_| LineSyntax::default())
        .collect::<Vec<_>>();
    let parameter_groups = source_layout
        .parameter_groups
        .iter()
        .copied()
        .collect::<HashSet<_>>();
    let repeated_parameter_groups = source_layout
        .repeated_parameter_groups
        .iter()
        .copied()
        .collect::<HashSet<_>>();
    let where_predicates = source_layout
        .where_predicates
        .iter()
        .copied()
        .collect::<HashSet<_>>();
    let mut delimiter_depth = 0usize;
    let mut brace_depth = 0usize;
    let mut brace_delimiter_baselines = vec![0usize];
    let tokens = lex(source).map_err(|error| error.to_string())?;
    for token in &tokens {
        if token.kind == TokenKind::Newline {
            continue;
        }
        if token.kind == TokenKind::Eof {
            break;
        }
        let line = &mut lines[token.line - 1];
        if line.first.is_none() {
            line.first = Some(token.kind.clone());
            line.first_byte = Some(token.start_byte);
            line.delimiter_depth = delimiter_depth.saturating_sub(
                brace_delimiter_baselines
                    .last()
                    .copied()
                    .unwrap_or_default(),
            );
            line.brace_depth = brace_depth;
        }
        if parameter_groups.contains(&token.start_byte) {
            line.has_parameter_group = true;
        }
        if repeated_parameter_groups.contains(&token.start_byte) {
            line.has_repeated_parameter_group = true;
        }
        if line.first_byte == Some(token.start_byte) {
            line.is_parameter_group = parameter_groups.contains(&token.start_byte);
            line.is_repeated_parameter_group =
                repeated_parameter_groups.contains(&token.start_byte);
            line.is_where_predicate = where_predicates.contains(&token.start_byte);
        }
        if line.code_token_count == line.leading_delimiter_closes
            && matches!(token.kind, TokenKind::RParen | TokenKind::RBracket)
        {
            line.leading_delimiter_closes += 1;
        }
        line.code_token_count += 1;
        line.last = Some(token.kind.clone());
        match token.kind {
            TokenKind::LBrace => {
                brace_delimiter_baselines.push(delimiter_depth);
                brace_depth += 1;
            }
            TokenKind::RBrace => {
                brace_depth = brace_depth.saturating_sub(1);
                brace_delimiter_baselines.pop();
                if brace_delimiter_baselines.is_empty() {
                    brace_delimiter_baselines.push(0);
                }
            }
            TokenKind::LParen | TokenKind::LBracket => delimiter_depth += 1,
            TokenKind::RParen | TokenKind::RBracket => {
                delimiter_depth = delimiter_depth.saturating_sub(1);
            }
            _ => {}
        }
    }

    let mut declaration_continuation = false;
    let mut previous_last = None;
    for (index, line) in lines.iter_mut().enumerate() {
        line.delimiter_indent = line
            .delimiter_depth
            .saturating_sub(line.leading_delimiter_closes);
        let continues_declaration = line.is_parameter_group
            || line.is_repeated_parameter_group
            || declaration_continuation
                && matches!(
                    line.first,
                    Some(TokenKind::Colon | TokenKind::Equal | TokenKind::FatArrow)
                );
        let operator_continuation = index != 0
            && !continues_declaration
            && line.first.is_some()
            && previous_last.as_ref().is_some_and(is_continuation_operator)
            && line.delimiter_indent == 0;
        line.continuation = usize::from(line.brace_depth == 0 && continues_declaration)
            + usize::from(operator_continuation);
        if line.is_where_predicate && index != 0 {
            line.continuation = 1;
        }
        declaration_continuation = (line.has_parameter_group || line.has_repeated_parameter_group)
            && !matches!(line.first, Some(TokenKind::Equal));
        if continues_declaration && line.first == Some(TokenKind::Colon) {
            declaration_continuation = true;
        }
        previous_last = line.last.clone();
    }
    Ok(lines)
}

fn is_continuation_operator(kind: &TokenKind) -> bool {
    matches!(
        kind,
        TokenKind::Dot
            | TokenKind::QuestionDot
            | TokenKind::QuestionQuestion
            | TokenKind::Equal
            | TokenKind::EqualEqual
            | TokenKind::BangEqual
            | TokenKind::Plus
            | TokenKind::PlusEqual
            | TokenKind::Minus
            | TokenKind::MinusEqual
            | TokenKind::Star
            | TokenKind::StarEqual
            | TokenKind::Slash
            | TokenKind::SlashEqual
            | TokenKind::Percent
            | TokenKind::PercentEqual
            | TokenKind::Less
            | TokenKind::LessEqual
            | TokenKind::Greater
            | TokenKind::GreaterEqual
            | TokenKind::AndAnd
            | TokenKind::OrOr
            | TokenKind::Amp
            | TokenKind::AmpEqual
            | TokenKind::Pipe
            | TokenKind::PipeEqual
            | TokenKind::Caret
            | TokenKind::CaretEqual
            | TokenKind::Shl
            | TokenKind::ShlEqual
            | TokenKind::Shr
            | TokenKind::ShrEqual
    )
}

fn semantic_token_kinds(source: &str) -> Result<Vec<TokenKind>, String> {
    let mut kinds = lex(source)
        .map_err(|error| error.to_string())?
        .into_iter()
        .map(|token| token.kind)
        .collect::<Vec<_>>();
    let eof = kinds.pop();
    while kinds.last() == Some(&TokenKind::Newline) {
        kinds.pop();
    }
    if let Some(eof) = eof {
        kinds.push(eof);
    }
    Ok(kinds)
}

#[derive(Default)]
struct ScanState {
    brace_depth: usize,
    block_comment_depth: usize,
}

#[derive(Default)]
struct LineAnalysis {
    starts_with_close: bool,
    opens: usize,
    closes: usize,
}

impl ScanState {
    fn scan_line(&mut self, line: &str) -> LineAnalysis {
        let chars = line.chars().collect::<Vec<_>>();
        let mut analysis = LineAnalysis::default();
        let mut index = 0;
        let mut string = false;
        let mut escaped = false;
        let mut saw_code = false;

        while index < chars.len() {
            let ch = chars[index];
            let next = chars.get(index + 1).copied();
            if self.block_comment_depth != 0 {
                if ch == '/' && next == Some('*') {
                    self.block_comment_depth += 1;
                    index += 2;
                } else if ch == '*' && next == Some('/') {
                    self.block_comment_depth -= 1;
                    index += 2;
                } else {
                    index += 1;
                }
                continue;
            }
            if string {
                if escaped {
                    escaped = false;
                } else if ch == '\\' {
                    escaped = true;
                } else if ch == '"' {
                    string = false;
                }
                index += 1;
                continue;
            }
            if ch == '/' && next == Some('/') {
                break;
            }
            if ch == '/' && next == Some('*') {
                self.block_comment_depth += 1;
                index += 2;
                continue;
            }
            if ch == '"' {
                string = true;
                saw_code = true;
                index += 1;
                continue;
            }
            if !ch.is_whitespace() {
                if ch == '{' {
                    analysis.opens += 1;
                } else if ch == '}' {
                    analysis.closes += 1;
                    if !saw_code {
                        analysis.starts_with_close = true;
                    }
                }
                saw_code = true;
            }
            index += 1;
        }
        analysis
    }
}

#[cfg(test)]
mod tests {
    use super::format_source;
    use crate::parser::parse;

    #[test]
    fn formats_indentation_comments_and_trailing_space_idempotently() {
        let source = "let main = { (): i32 =>    \n// { stays a comment\nif(true) {\n/* nested {\n   /* } */\n*/\n42\n} else: {\n0\n}\n}\n";
        let expected = "let main = {\n  (): i32 =>\n  // { stays a comment\n  if(true) {\n    /* nested {\n    /* } */\n    */\n    42\n  } else: {\n    0\n  }\n}\n";
        let formatted = format_source(source).expect("format valid source");
        assert_eq!(formatted, expected);
        assert_eq!(
            format_source(&formatted).expect("format output again"),
            formatted
        );
    }

    #[test]
    fn preserves_expression_newlines_without_creating_calls() {
        let source =
            "let apply = { (value: i32): i32 =>  value }\nlet main = { (): i32 => \napply\n42\n}\n";
        let expected =
            "let apply = { (value: i32): i32 =>  value }\nlet main = {\n  (): i32 =>\n  apply\n  42\n}\n";
        assert_eq!(format_source(source).expect("format calls"), expected);
    }

    #[test]
    fn expands_nested_blocks_and_their_leading_closing_braces() {
        let source = "let run = { (move action: (): i32): i32 =>  action() }\nlet main = { (): i32 =>  run { 42 } }\nlet other = { (): i32 =>  unsafe {\n0\n} }\n";
        let expected = "let run = { (move action: (): i32): i32 =>  action() }\nlet main = {\n  (): i32 =>  run { 42 }\n}\nlet other = {\n  (): i32 =>  unsafe {\n    0\n  }\n}\n";
        let formatted = format_source(source).expect("format nested blocks");
        assert_eq!(formatted, expected);
        assert_eq!(
            format_source(&formatted).expect("format output again"),
            formatted
        );
    }

    #[test]
    fn indents_parameter_groups_and_match_arms() {
        let source = "let apply = { <e: effects> with<e>\n(action: with<e>(i32): i32)\n(value: i32): i32 =>  action(value) }\n\nlet main = { (): i32 => \nmatch(true) {\ntrue => match(false) {\nfalse => apply()(42),\ntrue => 0,\n},\nfalse => 0,\n}\n}\n";
        let expected = "let apply = { <e: effects> with<e>\n  (action: with<e>(i32): i32)\n  (value: i32): i32 =>  action(value) }\n\nlet main = {\n  (): i32 =>\n  match(true) {\n    true => match(false) {\n      false => apply()(42),\n      true => 0,\n    },\n    false => 0,\n  }\n}\n";
        let formatted = format_source(source).expect("format continuations");
        assert_eq!(formatted, expected);
        assert_eq!(
            format_source(&formatted).expect("format output again"),
            formatted
        );
    }

    #[test]
    fn expands_inline_arms_when_a_match_already_spans_lines() {
        let source = "let next = { <r: region>\n(batch: Borrow<mut><r><Batch>)\n(): Option<Transaction> =>\nlet transaction: Option<Transaction> = match(batch.index) { 0 => Some(Transaction.Credit(30)), 1 => Some(Transaction.Debit(8)), _ => None,\n}\ntransaction\n}\n";
        let expected = "let next = { <r: region>\n  (batch: Borrow<mut><r><Batch>)\n  (): Option<Transaction> =>\n  let transaction: Option<Transaction> = match(batch.index) {\n    0 => Some(Transaction.Credit(30)),\n    1 => Some(Transaction.Debit(8)),\n    _ => None,\n  }\n  transaction\n}\n";
        let formatted = format_source(source).expect("format multiline match arms");
        assert_eq!(formatted, expected);
        assert_eq!(
            format_source(&formatted).expect("format multiline match again"),
            formatted
        );

        let nested = "let main = { (): i32 =>\nmatch(true) { true => match(false) { false => 42, true => 0, }, false => 0,\n}\n}\n";
        let formatted = format_source(nested).expect("format nested compact match");
        assert!(
            formatted.contains("true => match(false) { false => 42, true => 0, },"),
            "{formatted}"
        );
    }

    #[test]
    fn puts_named_function_groups_on_new_lines_for_multiline_bodies() {
        let source = "let credit = {\n(state: Borrow<mut><Ledger>)(amount: i32): () =>\nstate.balance = state.balance + amount\nstate.processed = state.processed + 1\n}\nlet compact = { (state: Borrow<mut><Ledger>)(amount: i32): i32 =>  amount }\n";
        let expected = "let credit = {\n  (state: Borrow<mut><Ledger>)\n  (amount: i32): () =>\n  state.balance = state.balance + amount\n  state.processed = state.processed + 1\n}\nlet compact = { (state: Borrow<mut><Ledger>)(amount: i32): i32 =>  amount }\n";
        let formatted = format_source(source).expect("format callable parameter groups");
        assert_eq!(formatted, expected);
        assert_eq!(
            format_source(&formatted).expect("format callable groups again"),
            formatted
        );
    }

    #[test]
    fn formats_delimiters_where_clauses_and_expression_continuations() {
        let source = "let marker = trait {}\nlet duplicate = { <t: type>(value: t): t requires(t is Copyable && t is marker) => \nvalue\n}\n\nlet add = { (\nleft: i32,\nright: i32,\n): i32 => \nleft +\nright\n}\n\nlet main = { (): i32 => \nlet values = [\n40,\n2,\n]\nlet grouped =\n(values[0] + values[1])\nadd(\nvalues[0],\nvalues[1],\n) + grouped - 42\n}\n";
        let expected = "let marker = trait {}\nlet duplicate = { <t: type>\n  (value: t): t requires(t is Copyable && t is marker) =>\n  value\n}\n\nlet add = {\n  (\n    left: i32,\n    right: i32,\n  ): i32 =>\n  left +\n    right\n}\n\nlet main = {\n  (): i32 =>\n  let values = [\n    40,\n    2,\n  ]\n  let grouped =\n    (values[0] + values[1])\n  add(\n    values[0],\n    values[1],\n  ) + grouped - 42\n}\n";
        let formatted = format_source(source).expect("format syntax continuations");
        assert_eq!(formatted, expected);
        assert_eq!(
            format_source(&formatted).expect("format output again"),
            formatted
        );
    }

    #[test]
    fn canonicalizes_a_space_before_brace_application() {
        let source = "let choose = { <t: type>[left: t]{right: t}(fallback: t): t =>  left }\nlet value = choose<i32>[1]{2}(3)\n";
        let expected = "let choose = { <t: type>[left: t]{right: t}(fallback: t): t =>  left }\nlet value = choose<i32>[1] {2}(3)\n";
        assert_eq!(format_source(source).unwrap(), expected);

        let source = "let count = { {value: usize}: usize =>  value }\nlet consume = { (value: Array<i32><count{2}>): i32 =>  value[0] }\n";
        let expected = "let count = { {value: usize}: usize =>  value }\nlet consume = {\n  (value: Array<i32><count {2}>): i32 =>  value[0]\n}\n";
        assert_eq!(format_source(source).unwrap(), expected);
    }

    #[test]
    fn spaces_brace_application_after_a_same_line_block_comment() {
        let source = "let count = { {value: usize}: usize =>  value }\nlet consume = { (value: Array<i32><count/* units */{2}>): i32 =>  value[0] }\n";
        let expected = "let count = { {value: usize}: usize =>  value }\nlet consume = {\n  (value: Array<i32><count/* units */ {2}>): i32 =>  value[0]\n}\n";
        assert_eq!(format_source(source).unwrap(), expected);
    }

    #[test]
    fn canonicalizes_exact_brace_spacing_in_guards() {
        let source = "let value = match(input) { item if predicate\t \t{1} => item }\n";
        let formatted = format_source(source).unwrap();
        assert!(formatted.contains("predicate {1}"), "{formatted}");
        assert!(!formatted.contains("predicate\t"), "{formatted}");
        parse(&formatted).expect("formatted guard must reparse");
    }

    #[test]
    fn block_comment_newlines_do_not_become_brace_calls() {
        assert!(format_source("let value = predicate /* hidden\nnewline */ { 1 }\n").is_err());
    }

    #[test]
    fn spaces_a_for_iterable_brace_application_without_changing_its_role() {
        let source =
            "let visit = { (): () => \n  for (counter{current: 0, end: 4}) { value => value }\n}\n";
        let expected =
            "let visit = {\n  (): () =>\n  for (counter {current: 0, end: 4}) { value => value }\n}\n";
        let formatted = format_source(source).expect("format parenthesized `for` iterable");
        assert_eq!(formatted, expected);
        parse(&formatted).expect("formatted constructor remains the `for` iterable");
    }

    #[test]
    fn preserves_minimal_syntax_contract_tokens_idempotently() {
        let source = "let marker = trait {}\nlet bounded = trait<requires: self is marker> {\n}\nlet cell = <t: type> struct { value: t }\nextend(cell<t>)<requires: t is marker> {\n}\nlet guarded = { <t: type>(value: t): t requires(t is marker) => \nvalue\n}\ntest(\"minimal contracts\") {\nlet value = 1\n}\n";
        let expected = "let marker = trait {}\nlet bounded = trait<requires: self is marker> {\n}\nlet cell = <t: type> struct { value: t }\nextend(cell<t>)<requires: t is marker> {\n}\nlet guarded = { <t: type>\n  (value: t): t requires(t is marker) =>\n  value\n}\ntest(\"minimal contracts\") {\n  let value = 1\n}\n";
        let formatted = format_source(source).expect("format minimal syntax contracts");
        assert_eq!(formatted, expected);
        assert_eq!(
            format_source(&formatted).expect("format output again"),
            formatted
        );
    }

    #[test]
    fn preserves_trait_and_effect_member_declarations_idempotently() {
        let source = "let marker = trait {}\nlet protocol = trait {\nItem: <r: region>: type\nArgs: <T: type>: parameters\nread: <T: type>(self)(value: T): T requires(T is marker) = value\n}\nlet state = effect {\nget: (): i32\n}\n";
        let formatted = format_source(source).expect("format trait and effect declarations");
        parse(&formatted).expect("formatted member declarations must reparse");
        assert_eq!(
            format_source(&formatted).expect("format output again"),
            formatted
        );
    }

    #[test]
    fn formats_generated_handle_members_as_ordinary_labeled_calls() {
        let source = "let run = { (): i32 => state.handle{get:{(resume)=>resume(42)},action:{state.get()},} }\n";
        let formatted = format_source(source).expect("format generated handle call");
        assert!(formatted.contains("state.handle {"), "{formatted}");
        assert!(formatted.contains("get:"), "{formatted}");
        assert!(formatted.contains("action:"), "{formatted}");
        assert_eq!(
            format_source(&formatted).expect("format output again"),
            formatted
        );
    }

    #[test]
    fn does_not_treat_closure_parameters_as_declaration_continuations() {
        let source = "let main = { (): i32 => \nlet closure = { (left: i32) =>  do {\nleft\n}\n}\nclosure(42)\n}\n";
        let expected = "let main = {\n  (): i32 =>\n  let closure = {\n    (left: i32) =>  do {\n      left\n    }\n  }\n  closure(42)\n}\n";
        let formatted = format_source(source).expect("format closure parameters");
        assert_eq!(formatted, expected);
        assert_eq!(
            format_source(&formatted).expect("format output again"),
            formatted
        );
    }

    #[test]
    fn preserves_named_pattern_callables_idempotently() {
        let source = "let select = {\ntrue => 42,\nfalse => 0,\n}\n";
        let expected = "let select = {\n  true => 42,\n  false => 0,\n}\n";
        let formatted = format_source(source).expect("format named pattern callable");
        assert_eq!(formatted, expected);
        assert_eq!(
            format_source(&formatted).expect("format output again"),
            formatted
        );
    }

    #[test]
    fn rejects_invalid_source_without_rewriting_it() {
        let error = format_source("let main( = {\n").expect_err("invalid source must fail");
        assert!(error.contains("signature groups must follow `=`"));
    }
}

use super::support::*;
use std::io::Write;
use std::process::Stdio;

#[test]
fn file_authority_and_ownership_are_static_contracts() {
    let pure = check_source(
        r#"let misuse(path: Borrow<core.string.str>): core.Result<std.io.IoError><std.io.File> = {
  std.io.open(path)(std.io.OpenOptions.read_only())
}
let main(): i32 = { 42 }"#,
    )
    .expect_err("pure file open must require io");
    assert!(
        pure.iter()
            .any(|diagnostic| diagnostic.contains("requires custom effect `std::io::io`")),
        "{pure:#?}"
    );

    let copied = check_source(
        r#"let duplicate(copy value: std.io.File): () = {}
let main(): i32 = { 42 }"#,
    )
    .expect_err("file owners must not be copyable");
    assert!(
        copied
            .iter()
            .any(|diagnostic| diagnostic.contains("does not implement `Copyable`")),
        "{copied:#?}"
    );
}

#[test]
fn explicit_close_failure_invalidates_before_the_single_host_attempt() {
    let source = r#"let close_calls(): i32 = foreign(c, "close_calls")

let abandon: with<std.io.io>
  (path: Borrow<core.string.str>): () = {
  match(std.io.open(path)(std.io.OpenOptions.read_only())) { Ok(value) => (), Err(_) => (), }
}

let main: with<std.io.io>(): i32 = {
  let path: String = "/dev/null"
  let view = path.as_str()
  let input = match(std.io.open(view)(std.io.OpenOptions.read_only())) { Ok(value) => value, Err(_) => return(1), }
  match(input.close()) { Ok(_) => return(2), Err(_) => (), }
  if unsafe { close_calls() } != 1 { return(3) }
  abandon(view)
  if unsafe { close_calls() } == 2 { 42 } else { 5 }
}"#;
    let ir = compile_source(source).expect("compile close-failure fixture");
    let output = link_and_run_ir_with_c(
        &ir,
        r#"#include <errno.h>
static int calls;
int close(int descriptor) {
  (void)descriptor;
  calls += 1;
  errno = EIO;
  return -1;
}
int close_calls(void) {
  return calls;
}"#,
        "close failure invalidation",
    );
    assert_eq!(output.status.code(), Some(42), "{}", output_text(&output));
}

#[test]
fn native_console_and_process_contracts_preserve_bytes_and_utf8() {
    let temporary = TestDirectory::new();
    let source = temporary.write(
        "io.sc",
        r#"let main: with<std.io.io>(): i32 = {
  let argument = match(std.io.argument_bytes(1)) { Some(value) => value, None => return(1), }
  if argument.len() != 3 || argument[0] != 255 || argument[1] != 111 || argument[2] != 107 {
    return(2)
  }
  match(std.io.arguments()) { Ok(_) => return(3), Err(error) => match(error.kind()) { InvalidData => (), _ => return(4), }, }
  let maybe_line = match(std.io.read_line()) { Ok(value) => value, _ => return(5), }
  let line = match(maybe_line) { Some(value) => value, None => return(5), }
  let expected: String = "hello\n"
  if line != expected { return(6) }
  let output: String = "stdout"
  let error: String = "stderr"
  let output_view = output.as_str()
  match(std.io.println(output_view)) { Err(_) => return(7), Ok(_) => (), }
  let error_view = error.as_str()
  match(std.io.eprintln(error_view)) { Err(_) => return(8), Ok(_) => 42, }
}"#,
    );
    let executable = temporary.join("io-program");
    let built = salic()
        .arg("build")
        .arg(&source)
        .arg("-o")
        .arg(&executable)
        .output()
        .expect("build native I/O fixture");
    assert!(built.status.success(), "{}", output_text(&built));

    use std::os::unix::ffi::OsStringExt;
    let mut child = Command::new(&executable)
        .arg(std::ffi::OsString::from_vec(vec![255, b'o', b'k']))
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .spawn()
        .expect("spawn native I/O fixture");
    child
        .stdin
        .take()
        .expect("piped stdin")
        .write_all(b"hello\n")
        .expect("write fixture stdin");
    let output = child.wait_with_output().expect("wait for I/O fixture");
    assert_eq!(output.status.code(), Some(42), "{}", output_text(&output));
    assert_eq!(output.stdout, b"stdout\n");
    assert_eq!(output.stderr, b"stderr\n");
}

#[test]
fn native_io_helpers_report_eof_and_broken_pipe() {
    let temporary = TestDirectory::new();
    let source = temporary.write(
        "io-errors.sc",
        r#"let main: with<std.io.io>(): i32 = {
  let mode = match(std.io.argument_bytes(1)) { Some(value) => value, None => return(1), }
  if mode[0] == 101 {
    let mut bytes: Array<u8><2> = [0, 0]
    let outcome = do {
      let buffer = bytes.as_slice<mut>()
      std.io.read_stdin_exact(buffer)
    }
    match(outcome) { Err(error) => match(error.kind()) { UnexpectedEof => 42, _ => 2, }, Ok(_) => 3, }
  } else {
    let mut bytes = alloc.Vec<u8>.with_capacity(1048576)
    let mut index: u64 = 0
    while { index < 1048576 } {
      bytes.push(120)
      index = index + 1
    }
    let view = bytes.as_slice()
    match(std.io.write_stdout_all(view)) { Err(error) => match(error.kind()) { BrokenPipe => 42, _ => 4, }, Ok(_) => 5, }
  }
}"#,
    );
    let executable = temporary.join("io-errors");
    let built = salic()
        .arg("build")
        .arg(&source)
        .arg("-o")
        .arg(&executable)
        .output()
        .expect("build native I/O error fixture");
    assert!(built.status.success(), "{}", output_text(&built));

    let eof = Command::new(&executable)
        .arg("eof")
        .stdin(Stdio::piped())
        .output()
        .expect("run EOF fixture");
    assert_eq!(eof.status.code(), Some(42), "{}", output_text(&eof));

    let mut broken = Command::new(&executable)
        .arg("pipe")
        .stdout(Stdio::piped())
        .spawn()
        .expect("spawn broken-pipe fixture");
    drop(broken.stdout.take());
    let status = broken.wait().expect("wait for broken-pipe fixture");
    assert_eq!(status.code(), Some(42));
}

#[test]
fn native_file_owners_support_options_seek_flush_limits_and_close() {
    let temporary = TestDirectory::new();
    let source = temporary.write(
        "files.sc",
        r#"let main: with<std.io.io>(): i32 = {
  let mut arguments = match(std.io.arguments()) { Ok(value) => value, Err(_) => return(1), }
  let path = arguments.remove(1)
  let missing = arguments.remove(1)
  let path_view = path.as_str()
  let missing_view = missing.as_str()
  let initial: Array<u8><3> = "abc"
  let initial_view = initial.as_slice()
  match(std.io.write_file(path_view)(initial_view)) { Err(_) => return(2), Ok(_) => (), }
  match(std.io.read_file(path_view)(2)) { Err(error) => match(error.kind()) { InvalidData => (), _ => return(3), }, Ok(_) => return(4), }
  let mut output = match(std.io.open(path_view)(std.io.OpenOptions.append_only())) { Ok(value) => value, Err(_) => return(5), }
  let suffix: Array<u8><1> = "d"
  let suffix_view = suffix.as_slice()
  match(output.write_all(suffix_view)) { Err(_) => return(6), Ok(_) => (), }
  match(output.flush()) { Err(_) => return(7), Ok(_) => (), }
  match(output.close()) { Err(_) => return(8), Ok(_) => (), }
  match(std.io.open(path_view)(std.io.OpenOptions.create_new())) { Err(error) => match(error.kind()) { AlreadyExists => (), _ => return(9), }, Ok(_) => return(10), }
  match(std.io.open(missing_view)(std.io.OpenOptions.read_only())) { Err(error) => match(error.kind()) { NotFound => (), _ => return(11), }, Ok(_) => return(12), }
  let invalid_options = std.io.OpenOptions.read_only().with_create(true)
  match(std.io.open(path_view)(invalid_options)) { Err(error) => match(error.kind()) { InvalidInput => (), _ => return(18), }, Ok(_) => return(19), }
  let mut nul_bytes = alloc.Vec<u8>.new()
  nul_bytes.push(97)
  nul_bytes.push(0)
  nul_bytes.push(98)
  let nul_path = match(alloc.string.string_from_utf8(nul_bytes)) { Ok(value) => value, Err(_) => return(20), }
  let nul_view = nul_path.as_str()
  match(std.io.open(nul_view)(std.io.OpenOptions.read_only())) { Err(error) => match(error.kind()) { InvalidInput => (), _ => return(21), }, Ok(_) => return(22), }
  let mut input = match(std.io.open(path_view)(std.io.OpenOptions.read_only())) { Ok(value) => value, Err(_) => return(13), }
  match(input.seek(0, End)) { Ok(4) => (), _ => return(14), }
  match(input.seek(0, Start)) { Ok(0) => (), _ => return(15), }
  let mut bytes: Array<u8><4> = [0, 0, 0, 0]
  let outcome = do {
    let buffer = bytes.as_slice<mut>()
    input.read_exact(buffer)
  }
  match(outcome) { Err(_) => return(16), Ok(_) => (), }
  if bytes[0] == 97 && bytes[1] == 98 && bytes[2] == 99 && bytes[3] == 100 {
    42
  } else {
    17
  }
}"#,
    );
    let executable = temporary.join("files");
    let built = salic()
        .arg("build")
        .arg(&source)
        .arg("-o")
        .arg(&executable)
        .output()
        .expect("build native file fixture");
    assert!(built.status.success(), "{}", output_text(&built));

    let path = temporary.join("data.bin");
    let missing = temporary.join("missing.bin");
    let output = Command::new(&executable)
        .arg(&path)
        .arg(&missing)
        .output()
        .expect("run native file fixture");
    assert_eq!(output.status.code(), Some(42), "{}", output_text(&output));
    assert_eq!(fs::read(path).expect("read file fixture"), b"abcd");
}

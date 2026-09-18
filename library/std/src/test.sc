// Private bridge from the compiler-generated test runner to the dedicated
// parent-owned Result pipe. This is not general source I/O authority.
let host_report(
  index: u64,
  status: u8,
  has_message: u8,
  data: Ptr<u8>,
  length: u64,
): i32 = foreign(c, "sali_host_test_report")

let send(
  index: u64,
  status: u8,
  has_message: u8,
  data: Ptr<u8>,
  length: u64,
): () = {
  if(unsafe { host_report(index, status, has_message, data, length) } != 0) {
    unsafe {
      raw_trap()
    }
  }
}

let report_pass(index: u64): bool = {
  let empty: u8 = 0
  send(index, 0, 0, ptr(borrow(empty)), 0)
  true
}

let report_with_message(
  index: u64,
  move message: core.string.String,
): bool = {
  let view = message.as_str()
  let bytes = view.as_bytes()
  let data = unsafe { raw_slice_ptr(bytes) }
  send(index, 1, 1, data, bytes.len())
  false
}

// Called only by the compiler-generated runner after one registration has
// returned through its source-backed failure handler and cleanup path.
let report(index: u64, move value: core.testing.Outcome): bool = {
  match(value) { Passed => report_pass(index), Failed(message) => report_with_message(index, message),
  }
}

// Emits the terminal frame and returns the native process summary status.
let finish(registrations: u64, failures: u64): i32 = {
  let empty: u8 = 0
  send(registrations, 2, 0, ptr(borrow(empty)), failures)
  if(failures == 0) { 0 } else: { 1 }
}

/// Fails the current test with an exact owned UTF-8 message.
pub let fail: with<core.error.throwing<core.string.String>>
  (move message: core.string.String): never = {
  core.error.throw(message)
}

/// Requires a condition to be true.
pub let assert: with<core.error.throwing<core.string.String>>(condition: bool): () = {
  if(!condition) {
    fail("assertion failed")
  }
}

/// Converts values with the core diagnostic-formatting contract into owned
/// assertion text without exposing the assertion helpers' writer choice.
pub let AssertionDebug = trait {
  let assertion_debug(self: Borrow<self>)(): core.string.String
}

extend(bool, AssertionDebug) {
  let assertion_debug(self: Borrow<self>)(): core.string.String = {
    let value: bool = self
    if(value) { "true" } else: { "false" }
  }
}

extend(core.string.UnicodeScalar, AssertionDebug) {
  let assertion_debug(self: Borrow<self>)(): core.string.String = {
    let mut writer = alloc.string.StringWriter.new()
    self.debug(writer)
    writer.finish()
  }
}

extend(core.string.str, AssertionDebug) {
  let assertion_debug(self: Borrow<self>)(): core.string.String = {
    let mut writer = alloc.string.StringWriter.new()
    let mut scalars = self.scalars()
    loop {
      match(scalars.next()) {
        Some(scalar) => writer.write_scalar(scalar), None => break(),
      }
    }
    writer.finish()
  }
}

extend(core.string.String, AssertionDebug) {
  let assertion_debug(self: Borrow<self>)(): core.string.String = {
    let mut writer = alloc.string.StringWriter.new()
    self.debug(writer)
    writer.finish()
  }
}

extend(u64, AssertionDebug) {
  let assertion_debug(self: Borrow<self>)(): core.string.String = {
    let mut writer = alloc.string.StringWriter.new()
    self.debug(writer)
    writer.finish()
  }
}

extend(u128, AssertionDebug) {
  let assertion_debug(self: Borrow<self>)(): core.string.String = {
    let mut writer = alloc.string.StringWriter.new()
    self.debug(writer)
    writer.finish()
  }
}

extend(i64, AssertionDebug) {
  let assertion_debug(self: Borrow<self>)(): core.string.String = {
    let mut writer = alloc.string.StringWriter.new()
    self.debug(writer)
    writer.finish()
  }
}

extend(i128, AssertionDebug) {
  let assertion_debug(self: Borrow<self>)(): core.string.String = {
    let mut writer = alloc.string.StringWriter.new()
    self.debug(writer)
    writer.finish()
  }
}

let equality_message(
  left: core.string.String,
  right: core.string.String,
): core.string.String = {
  let mut writer = alloc.string.StringWriter.new()
  "assert_eq failed\nleft: ".display(writer)
  left.display(writer)
  "\nright: ".display(writer)
  right.display(writer)
  writer.finish()
}

let inequality_message(value: core.string.String): core.string.String = {
  let mut writer = alloc.string.StringWriter.new()
  "assert_ne failed\nboth: ".display(writer)
  value.display(writer)
  writer.finish()
}

let unexpected_value_message(
  prefix: core.string.String,
)
  (value: core.string.String): core.string.String = {
  let mut writer = alloc.string.StringWriter.new()
  prefix.display(writer)
  value.display(writer)
  ")".display(writer)
  writer.finish()
}

/// Requires two values to compare equal. Each operand is evaluated once.
pub let assert_eq<T: type>: with<core.error.throwing<core.string.String>>
  (left: T)
  (right: T): () =
  requires(T is core.cmp.Eq<T> && T is AssertionDebug) {
  if(!(left == right)) {
    let left_text = left.assertion_debug()
    let right_text = right.assertion_debug()
    let message = equality_message(left_text, right_text)
    fail(message)
  }
}

/// Requires two values to compare unequal. Each operand is evaluated once.
pub let assert_ne<T: type>: with<core.error.throwing<core.string.String>>
  (left: T)
  (right: T): () =
  requires(T is core.cmp.Eq<T> && T is AssertionDebug) {
  if(left == right) {
    let value_text = left.assertion_debug()
    let message = inequality_message(value_text)
    fail(message)
  }
}

/// Extracts `Some`, failing when the Option is empty.
pub let expect_some<T: type>: with<core.error.throwing<core.string.String>>
  (move value: core.Option<T>): T = {
  match(value) { Some(value) => value, None => fail("expect_some failed: found none"),
  }
}

/// Requires `None`, formatting an unexpected payload exactly once.
pub let expect_none<T: type>: with<core.error.throwing<core.string.String>>
  (move value: core.Option<T>): () =
  requires(T is AssertionDebug) {
  match(value) {
    None => (), Some(value) => do {
      let value_text = value.assertion_debug()
      let message = unexpected_value_message(
        "expect_none failed: found some(",
      )(value_text)
      fail(message)
    },
  }
}

/// Extracts `Ok`, formatting an unexpected error exactly once.
pub let expect_ok<Error: type, T: type>:
with<core.error.throwing<core.string.String>>
  (move value: core.Result<Error><T>): T =
  requires(Error is AssertionDebug) {
  match(value) {
    Ok(value) => value, Err(error) => do {
      let error_text = error.assertion_debug()
      let message = unexpected_value_message(
        "expect_ok failed: found err(",
      )(error_text)
      fail(message)
    },
  }
}

/// Extracts `Err`, formatting an unexpected success value exactly once.
pub let expect_err<Error: type, T: type>:
with<core.error.throwing<core.string.String>>
  (move value: core.Result<Error><T>): Error =
  requires(T is AssertionDebug) {
  match(value) {
    Err(error) => error, Ok(value) => do {
      let value_text = value.assertion_debug()
      let message = unexpected_value_message(
        "expect_err failed: found ok(",
      )(value_text)
      fail(message)
    },
  }
}

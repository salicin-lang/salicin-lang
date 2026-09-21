let main with<std.io.io>
  (): i32 = {
  let text: String = "hello"
  let view = text.as_str()
  match(std.io.println(view)) {
    Ok(_) => 42,
    Err(_) => 1,
  }
}

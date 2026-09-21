/// Minimal allocation-free executor that polls one future until completion.
pub let Spin = struct {}

extend(Spin, core.async.Executor) {
  let run: <e: effects, F: type, T: type> with<e>
    (self: Borrow<mut><self>)
    (move future: F): T requires(F is core.async.Future<e> && F.Output == T) = {
    let mut current = future
    loop {
      match(current.poll()) {
        Ready(value) => break(value),
        Pending => continue(),
      }
    }
  }
}

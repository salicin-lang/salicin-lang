let abort = effect {
  stop: (value: i32): never
}

let main = { (): i32 =>
  abort.handle(do {
      abort.stop(42)
    }) {
    stop(value, resume) => do { resume(value) },
  }
}

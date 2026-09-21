let probe = effect {
  read: (): bool
}

let main: (): i32 = {
  probe.handle {
    read: { (resume) => return(resume(true)) },
    done: {
      (value) => return(if(value) { 42 } else: { 0 })
    },
    action: {
      let ignored = do { return(false) }
      return(probe.read()) },
  }
}

test<"algebraic_effect_done.sc"> {
  std.test.assert(main() == 42)
}

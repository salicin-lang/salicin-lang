let choose = effect {
  choose (): i32
}

let main = { (): i32 =>
  choose.handle(do {
      choose.choose()
    }) {
    choose(resume) => do {
      resume(20);
      resume(22)
    },
  }
}

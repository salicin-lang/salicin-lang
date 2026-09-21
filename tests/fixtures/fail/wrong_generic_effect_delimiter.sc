let state: <t: type> = effect {
  read: (): t
}

let program: (): i32 = {
  state(i32).read()
}

let main: (): i32 = { 42 }

let state<t: type> = effect {
  let read(): t
}

let program(): i32 = {
  state(i32).read()
}

let main(): i32 = { 42 }

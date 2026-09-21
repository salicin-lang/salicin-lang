let state: <s: type> = effect {
  get: (): s
}

let read = { (): i32 => state<i32>.get() }
let main = { (): i32 => read() }

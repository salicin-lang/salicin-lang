let token = struct { value: i32 }

let identity = <m: <p: parameters>: parameters, t: type>(m value: t): t => { value }

let main = (): i32 => {
  let token_value = token{ value: 42 }
  identity<m: copy, t: token>(token_value).value
}

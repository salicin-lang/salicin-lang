let identity = <m: <p: parameters>: parameters, t: type>(m value: t): t => { value }

let main = (): i32 => {
  let number = 20
  let moved = identity<move, i32>(number)
  moved + number
}

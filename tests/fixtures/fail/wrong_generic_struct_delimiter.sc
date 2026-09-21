let cell = <t: type> struct { value: t }

let main = {
  (): i32 =>
  let value = cell(i32) { value: 42 }
  value.value
}

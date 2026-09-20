let marker = trait {}
let cell = <t: type> struct { value: t }

extend(cell<t>)(requires: t is marker) {}

let main = (): i32 => { 42 }

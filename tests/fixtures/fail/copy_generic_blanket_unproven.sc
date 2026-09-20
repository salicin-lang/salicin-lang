let cell = <t: type> struct { value: t }

extend(cell<t>, Copyable) {}

let main = (): i32 => { 42 }

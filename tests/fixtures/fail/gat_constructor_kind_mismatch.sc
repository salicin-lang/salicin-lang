let wrong: <t: type>: type = t

let lend = trait {
  Item: <a: access>: type
}

let cell = struct { value: i32 }

extend(cell, lend) {
  let Item = wrong;
}

let main: (): i32 = { 0 }

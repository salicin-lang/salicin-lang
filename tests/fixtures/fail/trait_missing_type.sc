let convert = trait {
  Output: type
}

let number = struct { value: i32 }

extend(number, convert) {}

let main: (): i32 = { 0 }

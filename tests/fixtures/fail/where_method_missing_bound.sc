let measure = trait {
  measure: (self: Borrow<self>)(): i32
}

let read: <t: type> = { (value: Borrow<t>): i32 => value.measure() }

let main = { (): i32 => 0 }

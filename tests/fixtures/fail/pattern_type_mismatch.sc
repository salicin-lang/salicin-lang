let number = enum {
  value { value: i32 },
  Empty,
}

let classify(value: number): i32 = {
  match(value) { number.value( value: true ) => 42, number.value( value: _ ) => 0, number.Empty => 0,
  }
}

let main(): i32 = { classify(number.value( value: 42 )) }

let mark = enum {
  value { value: i32 },
  Empty,
}

extend<mark, Copyable> {}

let pixel = struct { value: i32 }

extend<pixel, Copyable> {}

let score(mark: mark): i32 = {
  match(mark) {
    mark.value( value: value ) => value,
    mark.Empty => 0,
  }
}

let main(): i32 = {
  let mark = mark.value { value: 10 }
  let pixels: Array<pixel><2> = [pixel { value: 20 }, pixel { value: 2 }]
  score(mark) + score(mark) + pixels[0].value + pixels[1].value
}

test<"copy_nominal_enum_array.sc"> {
  std.test.assert(main() == 42)
}

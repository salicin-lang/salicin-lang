let holder: <r: region> = struct { value: Borrow<r><i32> }
let main: (): i32 = { 42 }

test<"stored_borrow_field.sc"> {
  std.test.assert(main() == 42)
}

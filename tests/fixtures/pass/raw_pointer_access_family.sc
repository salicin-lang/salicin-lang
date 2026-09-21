let preserve: <a: access><t: type>(pointer: Ptr<a><t>): Ptr<a><t> = { pointer }

let main: (): i32 = {
  let shared_value = 40
  let mut mutable_value = 1
  let shared_pointer = ptr<i32>(borrow(shared_value))
  let mutable_pointer = ptr<mut>(borrow<mut>(mutable_value))
  let shared = preserve(shared_pointer)
  let mutable = preserve<mut>(mutable_pointer)
  unsafe {
    *mutable = *mutable + 1
    *shared + *mutable
  }
}

test<"raw_pointer_access_family.sc"> {
  std.test.assert(main() == 42)
}

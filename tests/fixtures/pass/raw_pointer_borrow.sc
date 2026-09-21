let shared<r: region>
  (anchor: Borrow<r><i32>)
  (pointer: Ptr<mut><i32>): Borrow<r><i32> = {
  unsafe {
    raw_borrow(pointer, borrow(anchor))
  }
}

let mutable<r: region>
  (anchor: Borrow<mut, r><i32>)
  (pointer: Ptr<mut><i32>): Borrow<mut, r><i32> = {
  unsafe {
    raw_borrow<mut>(pointer, borrow<mut>(anchor))
  }
}

let main(): i32 = {
  let pointer = unsafe {
    raw_alloc<i32>(size_of<i32>, align_of<i32>)
  }
  unsafe {
    raw_init(pointer, 20)
  }
  let mut anchor = 0
  let first = do {
    let reference = shared<anchor>(pointer)
    reference
  }
  do {
    let reference = mutable<anchor>(pointer)
    reference = 22
  }
  let second = unsafe {
    raw_take(pointer)
  }
  unsafe {
    raw_dealloc(pointer, size_of<i32>, align_of<i32>)
  }
  first + second
}

test<"raw_pointer_borrow.sc"> {
  std.test.assert(main() == 42)
}

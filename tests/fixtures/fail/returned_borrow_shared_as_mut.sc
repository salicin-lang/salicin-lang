let bad<r: region>(value: Borrow<r><i32>): Borrow<mut, r><i32> = { borrow(value) }
let main(): i32 = { 42 }

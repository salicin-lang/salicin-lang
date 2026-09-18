let pair = struct { left: i32, right: i32 }
let holder<t: type> = struct { value: t }

let right_view = trait {
  let view<r: region>(self: Borrow<r><self>)(): Borrow<r><i32>
  }

let left<r: region>(pair: Borrow<r><pair>): Borrow<r><i32> = { borrow(pair.left) }

let left_mut<r: region>
  (pair: Borrow<mut, r><pair>): Borrow<mut, r><i32> = { borrow<mut>(pair.left) }

let forward<r: region>(pair: Borrow<r><pair>): Borrow<r><i32> = { left(pair) }

let same<r: region, t: type>(value: Borrow<r><t>): Borrow<r><t> = { borrow(value) }

let forwarded_method<r: region>(pair: Borrow<r><pair>): Borrow<r><i32> = { pair.right_method() }

let inferred_left(pair: Borrow<pair>): Borrow<i32> = { borrow(pair.left) }

let inferred_same<t: type>(value: Borrow<t>): Borrow<t> = { borrow(value) }

let inferred_forward<r: region>(pair: Borrow<r><pair>): Borrow<r><i32> = { inferred_left(pair) }

extend(pair) {
  let right_ref<r: region>(pair: Borrow<r><pair>): Borrow<r><i32> = { borrow(pair.right) }

  let right_method<r: region>(self: Borrow<r><self>)(): Borrow<r><i32> = { borrow(self.right) }

  let left_mut_method<r: region>
    (self: Borrow<mut, r><self>)(): Borrow<mut, r><i32> = { borrow<mut>(self.left) }

  let inferred_right(self: Borrow<self>)(): Borrow<i32> = { borrow(self.right) }

  let inferred_left_mut(self: Borrow<mut><self>)(): Borrow<mut><i32> = { borrow<mut>(self.left) }
}

extend(holder<t>) {
  let get<r: region>(self: Borrow<r><self>)(): Borrow<r><t> = { borrow(self.value) }
}

extend(pair, right_view) {
  let view<r: region>(self: Borrow<r><self>)(): Borrow<r><i32> = { borrow(self.right) }
}

let main(): i32 = {
  let mut pair_value = pair{ left: 20, right: 0 }
  let holder_value = holder{ value: 0 }
  let before = do {
    let reference = forward(pair_value)
    let generic = same(value: pair_value)
    let associated = pair.right_ref(pair_value)
    let method = pair_value.right_method()
    let qualified = pair.right_method<self: pair_value>()
    let forwarded = forwarded_method(pair_value)
    let generic_method = holder_value.get()
    let trait_method = pair_value.view()
    let inferred = inferred_left(pair_value)
    let inferred_generic = inferred_same(value: pair_value)
    let inferred_forwarded = inferred_forward(pair_value)
    let inferred_method = pair_value.inferred_right()
    reference + generic.right + associated + method + qualified + forwarded + generic_method +
      trait_method + inferred + inferred_generic.right + inferred_forwarded + inferred_method - 40
  }
  do {
    let reference = pair_value.inferred_left_mut()
    reference = 22
  }
  before + pair_value.left
}

test("returned_borrow.sc") {
  std.test.assert(main() == 42)
}

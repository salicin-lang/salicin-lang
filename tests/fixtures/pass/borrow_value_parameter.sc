let read(value: Borrow<i32>): i32 = { value }

let forward<r: region>(value: Borrow<r><i32>): Borrow<r><i32> = { value }

let inferred_forward(value: Borrow<i32>): Borrow<i32> = { value }

let generic_read<t: type>(value: Borrow<t>): t
  = requires(t is Copyable) { value }

let forward_mut(value: Borrow<mut><i32>): Borrow<mut><i32> = { value }

let write(value: Borrow<mut><i32>)(replacement: i32): i32 = {
  value = replacement
  value
}

let main(): i32 = {
  let mut number = 20
  let before = do {
    let reference: Borrow<i32> = do {
      let inner: Borrow<i32> = borrow(number)
      inner
    }
    let forwarded = forward(reference)
    let inferred = inferred_forward(forwarded)
    read(inferred) + generic_read(reference) + generic_read(borrow(reference)) +
      generic_read(borrow(number)) - 60
  }
  let after = do {
    let reference: Borrow<mut><i32> = borrow<mut>(number)
    let forwarded = forward_mut(reference)
    write(forwarded)(22)
  }
  before + after
}

test("borrow_value_parameter.sc") {
  std.test.assert(main() == 42)
}

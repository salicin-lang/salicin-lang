let Vec = alloc.Vec

/// Collection of owned products.
pub let Inventory = struct {
  pub products: Vec<model.Product>,
}

/// Consumes a collection and summarizes all entries through their trait API.
pub let Summarize = trait {
  summarize: (move self)(): Summary
}

pub let Summary = struct {
  pub count: u64,
  pub total: i64,
  pub name_bytes: u64,
}

extend(Inventory) {
  let new: (): Inventory = {
    Inventory { products: Vec<model.Product>.new() }
  }

  let push: (self: Borrow<mut><self>)
    (move product: model.Product): () = {
    self.products.push(product)
  }
}

extend(Inventory, Summarize) {
  let summarize: (move self)
    (): Summary = {
    let mut owner = self
    let products = owner.products.take()
    let mut count: u64 = 0
    let mut total: i64 = 0
    let mut name_bytes: u64 = 0
    for products { product =>
      count = count + 1
      total = total + product.value()
      name_bytes = name_bytes + product.name_bytes()
    }
    Summary { count: count, total: total, name_bytes: name_bytes }
  }
}

test("inventory combines arrays slices vectors and Unicode") {
  let expected_name_bytes: Array<u64><2> = [1, 3]
  let byte_view = expected_name_bytes.as_slice()
  let first_bytes: u64 = match(byte_view.first()) {
    Some(value) => value,
    None => std.test.fail("expected first byte count"),
  }
  let last_bytes: u64 = match(byte_view.last()) {
    Some(value) => value,
    None => std.test.fail("expected last byte count"),
  }
  std.test.assert_eq<u64>(first_bytes + last_bytes)(4)

  let mut value = Inventory.new()
  value.push(model.Product.new("A", 2, 10))
  value.push(model.Product.new("柳", 3, 7))
  match(value.summarize()) {
    Summary(count: count, total: total, name_bytes: name_bytes) => do {
      std.test.assert(count == 2)
      std.test.assert(total == 41)
      std.test.assert(name_bytes == 4)
    },
  }
}

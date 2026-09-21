let String = core.string.String

/// Product data owns its validated UTF-8 name.
pub let Product = struct {
  pub name: String,
  pub units: i64,
  pub unit_price: i64,
}

/// Computes a value without exposing a product's representation.
pub let Valued = trait {
  value: (self: Borrow<self>)(): i64
}

extend<Product> {
  let new: (move name: String, units: i64, unit_price: i64): Product = {
    Product { name: name, units: units, unit_price: unit_price }
  }

  let name_bytes: (self: Borrow<self>)
    (): u64 = {
    self.name.len_bytes()
  }
}

extend<Product, Valued> {
  let value: (self: Borrow<self>)
    (): i64 = {
    self.units * self.unit_price
  }
}

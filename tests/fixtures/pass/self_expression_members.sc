let point = struct { raw: i32 }

extend(point) {
  let origin: self = self { raw: 40 }
  let new = (value: i32): self => { self { raw: value } }
  let shifted = (move self)(delta: i32): self => { self { raw: self.raw + delta } }
  let read = (self: Borrow<self>)(): i32 => { self.raw }
  let read = (value: self): i32 => { self.read(self: value)() }
}

let choice = enum { Some(i32), None }

extend(choice) {
  let unwrap = (move self)(): i32 => {
    match(self) { self.Some(value) => value, self.None => 0,
    }
  }
}

let rebuild = trait {
  let rebuild = (move self)(): self
  let read = (self: Borrow<self>)(): i32
  let twice = (self: Borrow<self>)(): i32 => { self.read() + self.read() }
}

let wrapper = struct { raw: i32 }

extend(wrapper, rebuild) {
  let rebuild = (move self)(): self => { self { raw: self.raw } }
  let read = (self: Borrow<self>)(): i32 => { self.raw }
}

let main = (): i32 => {
  let shifted = point.new(40).shifted(2)
  let point_value = point.read(shifted)
  let choice = choice.Some(42).unwrap()
  let wrapper = wrapper { raw: 42 }.rebuild().raw
  let default = wrapper { raw: 21 }.twice()
  let origin = point.origin.raw
  point_value + choice + wrapper + default + origin - 166
}

test("self_expression_members.sc") {
  std.test.assert(main() == 42)
}

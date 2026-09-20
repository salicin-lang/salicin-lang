let Index = core.ops.Index

let bag = struct { value: i32 }
extend(bag, Index<i32>) {
  let Output = i32;
  let index = <a: access>
    (self: Borrow<a><self>)
    (key: i32): Borrow<a><i32> => {
    borrow<a>(self.value)
  }
}

let main = (): i32 => {
  let mut bag = bag{ value: 1 }
  let item = borrow(bag[0])
  bag[0] = 42
  item
}

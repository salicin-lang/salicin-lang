let PartialOrd = core.ops.PartialOrd
let PartialOrdering = core.ops.PartialOrdering

let number = struct { value: i32, unordered: bool }

extend(number, PartialOrd<number>) {
  let partial_cmp: (self: Borrow<self>)
    (rhs: Borrow<number>): PartialOrdering = {
    if(self.unordered || rhs.unordered) { Unordered }
    else: {
      if(self.value < rhs.value) { Less }
      else: {
        if(self.value > rhs.value) { Greater }
        else: { Equal }
      }
    }
  }
}

let main: (): i32 = {
  let low = number { value: 1, unordered: false }
  let high = number { value: 2, unordered: false }
  let none = number { value: 0, unordered: true }
  if(low < high && low <= high && high > low && high >= low &&
    !(none < low) && !(none <= low) && !(none > low) && !(none >= low) ) {
    42
  } else: {
    0
  }
}

test("partial_ord_operator_trait.sc") {
  std.test.assert(main() == 42)
}

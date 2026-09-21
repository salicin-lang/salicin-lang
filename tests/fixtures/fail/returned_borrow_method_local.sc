let cell = struct { value: i32 }

extend(cell) {
  let get: <r: region>(self: Borrow<r><self>)(): Borrow<r><i32> = { borrow(self.value) }
}

let bad: <r: region>(seed: Borrow<r><i32>): Borrow<r><i32> = {
  let cell = cell { value: seed }
  cell.get()
}

let main: (): i32 = { 42 }

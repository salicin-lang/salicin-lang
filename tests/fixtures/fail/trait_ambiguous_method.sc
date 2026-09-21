let left_answer = trait {
  answer: (self: Borrow<self>)(): i32
}

let right_answer = trait {
  answer: (self: Borrow<self>)(): i32
}

let number = struct { value: i32 }

extend<number, left_answer> {
  let answer: (self: Borrow<self>)(): i32 = { self.value }
}

extend<number, right_answer> {
  let answer: (self: Borrow<self>)(): i32 = { self.value }
}

let main: (): i32 = {
  let number = number { value: 42 }
  number.answer()
}

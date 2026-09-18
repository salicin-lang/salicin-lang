let choose<t: type>(left: t): t = { left }
let choose<t: type>(right: t): t = { right }

let counter = struct { value: i32 }

extend(counter) {
  let add<t: type>(self: Borrow<self>)(left: t): t = { left }
  let add<t: type>(self: Borrow<self>)(right: t): t = { right }
}

let cell<t: type> = struct { value: t }

extend(cell<t>) {
  let choose(left: t): t = { left }
  let choose(right: t): t = { right }
  let add(self: Borrow<self>)(left: t): t = { left }
  let add(self: Borrow<self>)(right: t): t = { right }
}

let main(): i32 = {
  choose(left: 10) + cell.choose(right: 10) + cell<i32>{ value: 0 }.add(left: 22)
}

test("generic_overload_named.sc") {
  std.test.assert(main() == 42)
}

let cell: <t: type> = struct { value: t }

extend<cell<t>> {
  let take: (move self)(): t = { self.value }
}

let consume: <t: type>(move cell: cell<t>): t = { cell.take() }

let main: (): i32 = { consume(cell: cell { value: 42 }) }

test<"generic_inherent_from_generic_function.sc"> {
  std.test.assert(main() == 42)
}

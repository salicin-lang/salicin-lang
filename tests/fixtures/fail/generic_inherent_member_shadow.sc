let cell<t: type> = struct { value: t }

extend<cell<t>> {
  let invalid<t: type>(self: Borrow<self>)(): t = { self.value }
}

let main(): i32 = { 0 }

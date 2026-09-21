let Index = core.ops.Index

let resource = struct { value: i32 }
extend<resource, Droppable> {
  let drop: (self: Borrow<mut><self>)(): () = { }
}

let bag = struct { value: resource }
extend<bag, Index<i32>> {
  let Output = resource;
  let index: <a: access>(self: Borrow<a><self>)
    (key: i32): Borrow<a><resource> = {
    borrow<a>(self.value)
  }
}

let main: (): resource = {
  let bag = bag { value: resource { value: 42 } }
  bag[0]
}

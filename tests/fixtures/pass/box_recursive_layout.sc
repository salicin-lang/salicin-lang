let Option = core.Option

let Box = alloc.Box

let node = struct { value: i32, next: Option<Box<node>> }

let main(): i32 = {
  let tail = node { value: 42, next: None }
  let head = Box.new(tail)
  42
}

test<"box_recursive_layout.sc"> {
  std.test.assert(main() == 42)
}

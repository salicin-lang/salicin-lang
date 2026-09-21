let fixed<l: usize>: type = Array<i32><l>;

let keep = trait {
  Output<l: usize>: type

  keep<l: usize>(move value: Output<l>): Output<l>
  }

let marker = struct {}

extend<marker, keep> {
  let Output = fixed;

  let keep<l: usize>
    (move value: Array<i32><l>): Array<i32><l> = {
    value
  }
}

let main(): i32 = {
  let values = marker.keep<2>([20, 22])
  values[0] + values[1]
}

test<"gat_usize_family.sc"> {
  std.test.assert(main() == 42)
}

let fail = with<std.io.io>(message: core.string.String)(code: i32): i32 => {
  let view = message.as_str()
  match(std.io.eprintln(view)) { Ok(_) => code, Err(_) => code,
  }
}

let take_number = (
  arguments: Borrow<mut><alloc.Vec<core.string.String>>,
): core.Option<i64> => {
  let text = arguments.remove(1)
  let view = text.as_str()
  match(parser.decimal(view)) { Ok(value) => core.Option.Some(value), Err(_) => core.Option.None,
  }
}

let main = with<std.io.io>(): i32 => {
  let mut arguments = match(std.io.arguments()) { Ok(value) => value, Err(_) => return(fail("arguments are not valid UTF-8")(2)),
  }
  if(arguments.len() != 8) {
    return(fail("usage: inventory OUTPUT NAME UNITS PRICE NAME UNITS PRICE")(2))
  }

  let output_path = arguments.remove(1)
  let first_name = arguments.remove(1)
  let first_units = match(take_number(arguments)) { Some(value) => value, None => return(fail("invalid first units")(3)),
  }
  let first_price = match(take_number(arguments)) { Some(value) => value, None => return(fail("invalid first price")(3)),
  }
  let second_name = arguments.remove(1)
  let second_units = match(take_number(arguments)) { Some(value) => value, None => return(fail("invalid second units")(3)),
  }
  let second_price = match(take_number(arguments)) { Some(value) => value, None => return(fail("invalid second price")(3)),
  }

  let mut inventory = catalog.Inventory.new()
  inventory.push(model.Product.new(first_name, first_units, first_price))
  inventory.push(model.Product.new(second_name, second_units, second_price))
  let text = report.render(inventory.summarize())
  let text_view = text.as_str()
  let bytes = text_view.as_bytes()
  let output_view = output_path.as_str()
  match(std.io.write_file(output_view)(bytes)) { Err(_) => return(fail("could not write output")(4)), Ok(_) => (),
  }
  match(std.io.print(text_view)) { Err(_) => 5, Ok(_) => 0,
  }
}

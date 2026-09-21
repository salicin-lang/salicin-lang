let StringWriter = alloc.string.StringWriter

/// Formats the stable output consumed by both the CLI and its acceptance test.
pub let render(value: catalog.Summary): core.string.String = {
  let mut writer = StringWriter.new()
  "items=".display(writer)
  value.count.display(writer)
  "\ntotal=".display(writer)
  value.total.display(writer)
  "\nname_bytes=".display(writer)
  value.name_bytes.display(writer)
  "\n".display(writer)
  writer.finish()
}

test<"report output is deterministic"> {
  let value = catalog.Summary { count: 2, total: 41, name_bytes: 4 }
  let actual = render(value)
  let expected: String = "items=2\ntotal=41\nname_bytes=4\n"
  std.test.assert(actual == expected)
}

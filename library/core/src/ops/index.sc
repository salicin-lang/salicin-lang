/// Protocol used by indexed place syntax.
pub let Index = <Key: type> trait {
  /// Element type selected by the key.
  Output: type

  /// Borrows the selected element with the receiver's access.
  index: <a: access>
      (self: Borrow<a><self>)
      (key: Key): Borrow<a><Output>
}

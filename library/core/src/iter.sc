/// Protocol for stateful producers of sequential values.
pub let Iterator = trait {
  /// Element type yielded while the Iterator is borrowed for `R`.
  Item: <r: region>: type
  /// Advances the Iterator and returns the next element, if any.
  next: <r: region>(self: Borrow<mut><r><self>)
  (): core.Option<Item<r>>
  }

/// Protocol for values that can be converted into an Iterator.
pub let IntoIterator = trait {
  /// Iterator type produced from `Self`.
  Iter: type
  /// Consumes `self` and returns an Iterator over its values.
  into_iter: (move self)
  (): Iter
}

let Array = core.memory.Array
let Slice = core.memory.Slice

/// Constant item family for iterators that yield owned values.
pub let OwnedItem: <T: type><r: region>: type = T

/// Access-preserving item family for iterators that yield element borrows.
pub let BorrowedItem: <a: access, T: type><r: region>: type = Borrow<a><r><T>;

/// Owning Iterator over a fixed-size Array of Copyable values.
pub let ArrayIntoIter: <T: type>
  <l: usize> = struct {
  values: Array<T><l>,
  next_index: usize,
}

extend<ArrayIntoIter<T><l>, Iterator><requires: T is core.marker.Copyable> {
  let Item = OwnedItem<T>;
  let next: <r: region>
    (self: Borrow<mut><r><self>)
    (): core.Option<T> = {
    if(self.next_index == l) {
      None
    } else: {
      let value = self.values[self.next_index]
      self.next_index = self.next_index + 1
      Some(value)
    }
  }
}

extend<Array<T><l>, IntoIterator><requires: T is core.marker.Copyable> {
  let Iter = ArrayIntoIter<T><l>;
  let into_iter: (move self)
    (): ArrayIntoIter<T><l> = {
    ArrayIntoIter<T><l> { values: self, next_index: 0 }
  }
}

/// Access-preserving Iterator over a borrowed Slice.
pub let SliceIter: <a: access><T: type> = struct {
  /// Source view retained for the complete Iterator lifetime.
  values: Borrow<a><Slice<T>>,
  /// index of the next element to yield.
  next_index: u64,
}

extend<SliceIter<a><T>, Iterator> {
  let Item = BorrowedItem<a, T>;
  /// Yields one access-preserving Borrow tied to this `next` Borrow.
  let next: <r: region>
    (self: Borrow<mut><r><self>)
    (): core.Option<Item<r>> = {
    if(self.next_index == unsafe { raw_slice_len(self.values) }) {
      None
    } else: {
      let index = self.next_index
      self.next_index = self.next_index + 1
      Some(unsafe {
        raw_slice_at<a>(self.values, index)
      })
    }
  }
}

extend<SliceIter<a><T>, IntoIterator> {
  let Iter = SliceIter<a><T>;
  let into_iter: (move self)(): SliceIter<a><T> = {  self }
}

extend<Slice<T>> {
  /// Iterates over borrowed values while retaining source access.
  let iter: <a: access = shared>
    (self: Borrow<a><self>)
    (): SliceIter<a><T> = {
    SliceIter<a><T> { values: self, next_index: 0 }
  }
}

extend<Array<T><l>> {
  /// Iterates over borrowed elements without requiring them to be Copyable.
  let iter: <a: access = shared>
    (self: Borrow<a><self>)
    (): SliceIter<a><T> = {
    SliceIter<a><T> { values: self.as_slice<a>(), next_index: 0 }
  }
}

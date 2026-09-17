// Primitive pointer and layout contracts. The source declarations define the
// public identities and signatures; the compiler supplies representation,
// authority checks, and target-specific lowering after validating this module.
let Index = core.ops.Index

/// Fixed-size Array type with compile-time element type and length.
pub let Array<T: type>
  <l: usize>: type = builtin()

/// Dynamically sized contiguous sequence viewed through a Borrow.
pub let Slice<T: type>: type = builtin()

/// Routes fixed-size Array brackets through the source-defined indexing protocol.
extend(Array<T><l>, Index<usize>) {
  let Output = T
  let index<a: access>
    (self: Borrow<a><self>)
    (key: usize): Borrow<a><T> = builtin()
}

/// Provides access operations shared with slices and vectors.
extend(Array<T><l>) {
  /// Borrows all elements as a Slice with the same source region.
  let as_slice<a: access = shared>
    (self: Borrow<a><self>)(): Borrow<a><Slice<T>> = {
    unsafe {
      raw_array_slice<a>(self)
    }
  }

  /// Returns the number of elements.
  let len(self: Borrow<self>)(): u64 = {
    let values = self.as_slice()
    values.len()
  }

  /// Returns whether this Array contains no elements.
  let is_empty(self: Borrow<self>)(): bool = { self.len() == 0 }

  /// Borrows the element at `index`, or returns `None` when out of bounds.
  let get<a: access = shared>
    (self: Borrow<a><self>)
    (index: u64): core.Option<Borrow<a><T>> = {
    let values = self.as_slice<a>()
    values.get<a>(index)
  }

  /// Borrows the element at `index`, trapping when out of bounds.
  let at<a: access = shared>
    (self: Borrow<a><self>)
    (index: u64): Borrow<a><T> = {
    let values = self.as_slice<a>()
    values.at<a>(index)
  }

  /// Borrows the first element, or returns `None` when empty.
  let first<a: access = shared>
    (self: Borrow<a><self>)(): core.Option<Borrow<a><T>> = {
    self.get<a>(0)
  }

  /// Borrows the last element, or returns `None` when empty.
  let last<a: access = shared>
    (self: Borrow<a><self>)(): core.Option<Borrow<a><T>> = {
    let values = self.as_slice<a>()
    values.last<a>()
  }

  /// Borrows the first element accepted by `predicate`.
  let find<e: effects>: with<e>(self: Borrow<self>)(move predicate: with<e>((Borrow<T>): bool)): core.Option<Borrow<T>> = {
    let values = self.as_slice()
    values.find(predicate)
  }

  /// Returns the index of the first element accepted by `predicate`.
  let position<e: effects>: with<e>(self: Borrow<self>)(move predicate: with<e>((Borrow<T>): bool)): core.Option<u64> = {
    let values = self.as_slice()
    values.position(predicate)
  }

  /// Returns whether any element is accepted by `predicate`.
  let any<e: effects>: with<e>(self: Borrow<self>)(move predicate: with<e>((Borrow<T>): bool)): bool = {
    let values = self.as_slice()
    values.any(predicate)
  }

  /// Returns whether every element is accepted by `predicate`.
  let all<e: effects>: with<e>(self: Borrow<self>)(move predicate: with<e>((Borrow<T>): bool)): bool = {
    let values = self.as_slice()
    values.all(predicate)
  }

  /// Folds elements from left to right into `initial`.
  let fold<e: effects, Accumulator: type>: with<e>(self: Borrow<self>)(move initial: Accumulator)(move combine: with<e>((Accumulator, Borrow<T>): Accumulator)): Accumulator = {
    let values = self.as_slice()
    values.fold(initial)(combine)
  }

  /// Swaps two elements, trapping before mutation when either index is invalid.
  let swap(self: Borrow<mut><self>)(left: u64, right: u64): () = {
    let values = self.as_slice<mut>()
    values.swap(left, right)
  }

  /// Reverses all elements in place.
  let reverse(self: Borrow<mut><self>)(): () = {
    let values = self.as_slice<mut>()
    values.reverse()
  }
}

/// Provides equality-based membership for fixed-size arrays.
extend(Array<T><l>)
(requires: T is core.marker.Copyable && T is core.cmp.Eq<T>) {
  /// Returns whether this Array contains an element equal to `needle`.
  let contains(self: Borrow<self>)(copy needle: T): bool = {
    let values = self.as_slice()
    values.contains(needle)
  }
}

/// Provides copy-based mutation for fixed-size arrays.
extend(Array<T><l>)
(requires: T is core.marker.Copyable) {
  /// Replaces every element with a copy of `value`.
  let fill(self: Borrow<mut><self>)(copy value: T): () = {
    let values = self.as_slice<mut>()
    values.fill(value)
  }

  /// Copies an equally sized source Slice into this Array.
  let copy_from(self: Borrow<mut><self>)(source: Borrow<Slice<T>>): () = {
    let values = self.as_slice<mut>()
    values.copy_from(source)
  }

  /// Copies a source range within this Array with overlap-safe semantics.
  let copy_within(self: Borrow<mut><self>)
    (source_start: u64, source_end: u64, destination_start: u64): () = {
    let values = self.as_slice<mut>()
    values.copy_within(source_start, source_end, destination_start)
  }
}

/// Provides operations on a borrowed contiguous sequence.
extend(Slice<T>) {
  /// Returns the number of elements in this Slice.
  let len<a: access = shared>(self: Borrow<a><self>)(): u64 = {
    unsafe {
      raw_slice_len(self)
    }
  }

  /// Returns whether this Slice contains no elements.
  let is_empty<a: access = shared>(self: Borrow<a><self>)(): bool = {
    self.len<a>() == 0
  }

  /// Borrows the element at `index`, or returns `None` when out of bounds.
  let get<a: access = shared>
    (self: Borrow<a><self>)
    (index: u64): core.Option<Borrow<a><T>> = {
    if index >= self.len<a>() {
      core.Option.None
    } else {
      core.Option.Some(unsafe {
        raw_slice_at<a>(self, index)
      })
    }
  }

  /// Borrows the element at `index`, trapping if `index` is out of bounds.
  let at<a: access = shared>
    (self: Borrow<a><self>)
    (index: u64): Borrow<a><T> = {
    unsafe {
      raw_slice_at<a>(self, index)
    }
  }

  /// Borrows the first element, or returns `None` when empty.
  let first<a: access = shared>
    (self: Borrow<a><self>)(): core.Option<Borrow<a><T>> = {
    self.get<a>(0)
  }

  /// Borrows the last element, or returns `None` when empty.
  let last<a: access = shared>
    (self: Borrow<a><self>)(): core.Option<Borrow<a><T>> = {
    let length = self.len<a>()
    if length == 0 {
      core.Option.None
    } else {
      self.get<a>(length - 1)
    }
  }

  /// Borrows the first element accepted by `predicate`.
  let find<e: effects>: with<e>(self: Borrow<self>)(move predicate: with<e>((Borrow<T>): bool)): core.Option<Borrow<T>> = {
    let length = self.len()
    let mut index: u64 = 0
    while { index < length } {
      let item = self.at(index)
      if predicate(item) {
        return(self.get(index))
      }
      index = index + 1
    }
    core.Option.None
  }

  /// Returns the index of the first element accepted by `predicate`.
  let position<e: effects>: with<e>(self: Borrow<self>)(move predicate: with<e>((Borrow<T>): bool)): core.Option<u64> = {
    let length = self.len()
    let mut index: u64 = 0
    while { index < length } {
      let item = self.at(index)
      if predicate(item) {
        return(core.Option.Some(index))
      }
      index = index + 1
    }
    core.Option.None
  }

  /// Returns whether any element is accepted by `predicate`.
  let any<e: effects>: with<e>(self: Borrow<self>)(move predicate: with<e>((Borrow<T>): bool)): bool = {
    let length = self.len()
    let mut index: u64 = 0
    while { index < length } {
      let item = self.at(index)
      if predicate(item) {
        return(true)
      }
      index = index + 1
    }
    false
  }

  /// Returns whether every element is accepted by `predicate`.
  let all<e: effects>: with<e>(self: Borrow<self>)(move predicate: with<e>((Borrow<T>): bool)): bool = {
    let length = self.len()
    let mut index: u64 = 0
    while { index < length } {
      let item = self.at(index)
      if !predicate(item) {
        return(false)
      }
      index = index + 1
    }
    true
  }

  /// Folds elements from left to right into `initial`.
  let fold<e: effects, Accumulator: type>: with<e>(self: Borrow<self>)(move initial: Accumulator)(move combine: with<e>((Accumulator, Borrow<T>): Accumulator)): Accumulator = {
    let length = self.len()
    let mut value = initial
    let mut index: u64 = 0
    while { index < length } {
      let item = self.at(index)
      value = combine(value, item)
      index = index + 1
    }
    value
  }

  /// Swaps two elements, trapping before mutation when either index is invalid.
  let swap(self: Borrow<mut><self>)(left: u64, right: u64): () = {
    let length = self.len<mut>()
    if left >= length || right >= length {
      unsafe {
        raw_trap()
      }
    }
    if left != right {
      let values = unsafe {
        raw_slice_ptr<mut><self>
        }
      let left_pointer = unsafe {
        raw_offset(values, left)
      }
      let right_pointer = unsafe {
        raw_offset(values, right)
      }
      let left_value = unsafe {
        raw_take(left_pointer)
      }
      let right_value = unsafe {
        raw_take(right_pointer)
      }
      unsafe {
        raw_init(left_pointer, right_value)
        raw_init(right_pointer, left_value)
      }
    }
  }

  /// Reverses all elements in place.
  let reverse(self: Borrow<mut><self>)(): () = {
    let length = self.len<mut>()
    let mut left: u64 = 0
    while { left < length / 2 } {
      self.swap(left, length - 1 - left)
      left = left + 1
    }
  }
}

/// Provides equality-based membership for borrowed slices.
extend(Slice<T>)
(requires: T is core.marker.Copyable && T is core.cmp.Eq<T>) {
  /// Returns whether this Slice contains an element equal to `needle`.
  let contains(self: Borrow<self>)(copy needle: T): bool = {
    let length = self.len()
    if length > 0 {
      let values = unsafe {
        raw_slice_ptr(self)
      }
      let mut index: u64 = 0
      while { index < length } {
        let item = unsafe {
          *raw_offset(values, index)
        }
        if item == needle {
          return(true)
        }
        index = index + 1
      }
    }
    false
  }
}

/// Provides copy-based mutation for borrowed contiguous sequences.
extend(Slice<T>)
(requires: T is core.marker.Copyable) {
  /// Replaces every element with a copy of `value`.
  let fill(self: Borrow<mut><self>)(copy value: T): () = {
    let length = self.len<mut>()
    if length > 0 {
      let values = unsafe {
        raw_slice_ptr<mut><self>
        }
      let mut index: u64 = 0
      while { index < length } {
        unsafe {
          *raw_offset(values, index) = value
        }
        index = index + 1
      }
    }
  }

  /// Copies `source` into this Slice, trapping before mutation on a length mismatch.
  let copy_from(self: Borrow<mut><self>)(source: Borrow<Slice<T>>): () = {
    let length = self.len<mut>()
    if source.len() != length {
      unsafe {
        raw_trap()
      }
    }
    if length > 0 {
      let source_values = unsafe {
        raw_slice_ptr(source)
      }
      let destination_values = unsafe {
        raw_slice_ptr<mut><self>
        }
      let mut index: u64 = 0
      while { index < length } {
        unsafe {
          *raw_offset(destination_values, index) = *raw_offset(source_values, index)
        }
        index = index + 1
      }
    }
  }

  /// Copies `[source_start, source_end)` to `destination_start`.
  ///
  /// Overlap is supported. All bounds are validated before the first write.
  let copy_within(self: Borrow<mut><self>)
    (source_start: u64, source_end: u64, destination_start: u64): () = {
    let length = self.len<mut>()
    if source_start > source_end || source_end > length {
      unsafe {
        raw_trap()
      }
    }
    let count = source_end - source_start
    if destination_start > length || count > length - destination_start {
      unsafe {
        raw_trap()
      }
    }
    if count > 0 {
      let values = unsafe {
        raw_slice_ptr<mut><self>
        }
      if destination_start > source_start {
        let mut remaining = count
        while { remaining > 0 } {
          remaining = remaining - 1
          unsafe {
            *raw_offset(values, destination_start + remaining) =
              *raw_offset(values, source_start + remaining)
          }
        }
      } else {
        let mut offset: u64 = 0
        while { offset < count } {
          unsafe {
            *raw_offset(values, destination_start + offset) =
              *raw_offset(values, source_start + offset)
          }
          offset = offset + 1
        }
      }
    }
  }
}

/// Routes bracket access through the source-defined indexing protocol.
extend(Slice<T>, Index<u64>) {
  let Output = T
  let index<a: access>
    (self: Borrow<a><self>)
    (key: u64): Borrow<a><T> = {
    self.at<a>(key)
  }
}

/// Raw pointer type with access `A` and pointee `T`.
pub let Ptr<a: access = shared>
  <T: type>: type = builtin()

/// Forms a raw pointer from a Borrow with the same access.
pub let ptr<a: access = shared>
  <T: type>
  (value: Borrow<a><T>): Ptr<a><T> = builtin()

/// Provides operations shared by raw pointers at either access.
extend(Ptr<a><T>) {
  /// Returns the pointer `index` elements after this pointer.
  let offset: with<core.unsafe.unsafety>(self)(index: u64): Ptr<a><T> = {
    unsafe {
      raw_offset(self, index)
    }
  }
}

/// Provides operations that require mutable raw-pointer access.
extend(Ptr<mut><T>) {
  /// Initializes storage that is currently uninitialized.
  let init: with<core.unsafe.unsafety>(self)(value: T): () = {
    unsafe {
      raw_init(self, value)
    }
  }

  /// Moves a value out and leaves the storage uninitialized.
  let take: with<core.unsafe.unsafety>(self)(): T = {
    unsafe {
      raw_take(self)
    }
  }
}

/// Returns the target size of `T` in bytes.
pub let size_of<T: type>: u64 = builtin()

/// Returns the target alignment of `T` in bytes.
pub let align_of<T: type>: u64 = builtin()

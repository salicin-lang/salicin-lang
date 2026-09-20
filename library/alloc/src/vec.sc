let Option = core.Option
let Slice = core.Slice
let Index = core.ops.Index
let Iterator = core.iter.Iterator
let IntoIterator = core.iter.IntoIterator
let OwnedItem = core.iter.OwnedItem

/// Growable contiguous heap allocation for values of type `T`.
pub let Vec = <T: type> struct {
  /// Pointer to the start of the allocated storage.
  pointer: Ptr<mut><T>,
  /// Number of initialized elements.
  length: u64,
  /// Number of elements that fit in the allocated storage.
  storage_capacity: u64,
}

/// Computes the byte size needed to store `capacity` elements of `T`.
let vec_layout_size = { <T: type>(capacity: u64): u64 =>
    let element_size = size_of<T>;
  if(element_size != 0 && capacity > 18446744073709551615 / element_size) {
    unsafe {
      raw_trap()
    }
  }
  capacity * element_size
}

/// Allocates raw storage for `capacity` elements of `T`.
let vec_allocate = { <T: type>(capacity: u64): Ptr<mut><T> =>
    unsafe {
    raw_alloc<T>(vec_layout_size<T: T>(capacity), align_of<T>)
  }
}

/// Deallocates raw vector storage previously allocated for `capacity` elements.
let vec_deallocate = { <T: type>(pointer: Ptr<mut><T>, capacity: u64): () =>
    unsafe {
    raw_dealloc<T>(pointer, vec_layout_size<T: T>(capacity), align_of<T>)
  }
}

/// Creates an empty vector with zero capacity.
let vec_new = { <T: type>(): Vec<T> =>
    Vec<T> { pointer: vec_allocate<T: T>(0), length: 0, storage_capacity: 0 }
}

/// Creates an empty vector with storage for `capacity` elements.
let vec_with_capacity = { <T: type>(capacity: u64): Vec<T> =>
    Vec<T> { pointer: vec_allocate<T: T>(capacity), length: 0, storage_capacity: capacity }
}

/// Returns the number of initialized elements in `values`.
let vec_len = { <T: type>(values: Borrow<Vec<T>>): u64 =>  values.length }

/// Returns the number of elements that fit without reallocating.
let vec_capacity = { <T: type>(values: Borrow<Vec<T>>): u64 =>  values.storage_capacity }

/// Borrows the element at `index`, trapping if `index` is out of bounds.
let vec_at = { <a: access, r: region, T: type>
    (values: Borrow<a><r><Vec<T>>)(index: u64): Borrow<a><r><T> =>
    if(index >= values.length) {
    unsafe {
      raw_trap()
    }
  }
  unsafe {
    raw_borrow<a>(raw_offset(values.pointer, index), borrow<a>(values))
  }
}

/// Ensures that `values` can accept at least `additional` more elements.
let vec_reserve = { <T: type>(values: Borrow<mut><Vec<T>>)(additional: u64): () =>
    if(additional > 18446744073709551615 - values.length) {
    unsafe {
      raw_trap()
    }
  }
  let required_capacity = values.length + additional
  if(required_capacity > values.storage_capacity) {
    let mut new_capacity: u64 = if(values.storage_capacity == 0) {
      1
    } else: {
      values.storage_capacity
    }
    while(new_capacity < required_capacity) {
      new_capacity = if(new_capacity > 9223372036854775807) {
        required_capacity
      } else: {
        new_capacity * 2
      }
    }
    let new_pointer = vec_allocate<T>(new_capacity)
    let mut index: u64 = 0
    while(index < values.length) {
      let item = unsafe {
        raw_take(raw_offset(values.pointer, index))
      }
      unsafe {
        raw_init(raw_offset(new_pointer, index), item)
      }
      index = index + 1
    }
    vec_deallocate(values.pointer, values.storage_capacity)
    values.pointer = new_pointer
    values.storage_capacity = new_capacity
  }
}

/// Appends `value` to the end of `values`.
let vec_push = { <T: type>(values: Borrow<mut><Vec<T>>)(value: T): () =>
    vec_reserve(values)(1)
  unsafe {
    raw_init(raw_offset(values.pointer, values.length), value)
  }
  values.length = values.length + 1
}

/// Replaces the element at `index` and returns the previous element.
let vec_replace = { <T: type>(values: Borrow<mut><Vec<T>>)(index: u64)(value: T): T =>
    if(index >= values.length) {
    unsafe {
      raw_trap()
    }
  }
  let pointer = unsafe {
    raw_offset(values.pointer, index)
  }
  let previous = unsafe {
    raw_take(pointer)
  }
  unsafe {
    raw_init(pointer, value)
  }
  previous
}

/// Removes and returns the last element, or `None` if the vector is empty.
let vec_pop = { <T: type>(values: Borrow<mut><Vec<T>>): Option<T> =>
    if(values.length == 0) {
    Option<T>.None
  } else: {
    values.length = values.length - 1
    let value = unsafe {
      raw_take(raw_offset(values.pointer, values.length))
    }
    Option<T>.Some(value)
  }
}

/// Drops elements from the end until the vector length is at most `new_length`.
let vec_truncate = { <T: type>(values: Borrow<mut><Vec<T>>)(new_length: u64): () =>
    while(values.length > new_length) {
    values.length = values.length - 1
    let item = unsafe {
      raw_take(raw_offset(values.pointer, values.length))
    }
  }
}

/// Removes all elements from `values`.
let vec_clear = { <T: type>(values: Borrow<mut><Vec<T>>): () =>  vec_truncate(values)(0) }

/// Returns whether `values` has no initialized elements.
let vec_is_empty = { <T: type>(values: Borrow<Vec<T>>): bool =>  values.length == 0 }

/// Removes the element at `index` by moving the last element into its slot.
let vec_swap_remove = { <T: type>(values: Borrow<mut><Vec<T>>)(index: u64): T =>
    if(index >= values.length) {
    unsafe {
      raw_trap()
    }
  }
  let last_index = values.length - 1
  let removed = unsafe {
    raw_take(raw_offset(values.pointer, index))
  }
  if(index != last_index) {
    let last = unsafe {
      raw_take(raw_offset(values.pointer, last_index))
    }
    unsafe {
      raw_init(raw_offset(values.pointer, index), last)
    }
  }
  values.length = last_index
  removed
}

/// Swaps the elements at `left` and `right`.
let vec_swap = { <T: type>(values: Borrow<mut><Vec<T>>)(left: u64, right: u64): () =>
    if(left >= values.length || right >= values.length) {
    unsafe {
      raw_trap()
    }
  }
  if(left != right) {
    let left_value = unsafe {
      raw_take(raw_offset(values.pointer, left))
    }
    let right_value = unsafe {
      raw_take(raw_offset(values.pointer, right))
    }
    unsafe {
      raw_init(raw_offset(values.pointer, left), right_value)
      raw_init(raw_offset(values.pointer, right), left_value)
    }
  }
}

/// Reverses the order of initialized elements in place.
let vec_reverse = { <T: type>(values: Borrow<mut><Vec<T>>): () =>
    let mut left: u64 = 0
  while(left < values.length / 2) {
    let right = values.length - 1 - left
    vec_swap(values)(left, right)
    left = left + 1
  }
}

/// Inserts `value` at `index`, shifting later elements right.
let vec_insert = { <T: type>(values: Borrow<mut><Vec<T>>)(index: u64)(value: T): () =>
    if(index > values.length) {
    unsafe {
      raw_trap()
    }
  }
  vec_reserve(values)(1)
  let mut move_index = values.length
  while(move_index > index) {
    let previous_index = move_index - 1
    let item = unsafe {
      raw_take(raw_offset(values.pointer, previous_index))
    }
    unsafe {
      raw_init(raw_offset(values.pointer, move_index), item)
    }
    move_index = previous_index
  }
  unsafe {
    raw_init(raw_offset(values.pointer, index), value)
  }
  values.length = values.length + 1
}

/// Removes and returns the element at `index`, shifting later elements left.
let vec_remove = { <T: type>(values: Borrow<mut><Vec<T>>)(index: u64): T =>
    if(index >= values.length) {
    unsafe {
      raw_trap()
    }
  }
  let removed = unsafe {
    raw_take(raw_offset(values.pointer, index))
  }
  let mut move_index = index
  while(move_index + 1 < values.length) {
    let next_index = move_index + 1
    let item = unsafe {
      raw_take(raw_offset(values.pointer, next_index))
    }
    unsafe {
      raw_init(raw_offset(values.pointer, move_index), item)
    }
    move_index = next_index
  }
  values.length = values.length - 1
  removed
}

/// Moves all elements from `other` onto the end of `values`.
let vec_append = { <T: type>(values: Borrow<mut><Vec<T>>)(other: Borrow<mut><Vec<T>>): () =>
    let start = values.length
  let moved = other.length
  vec_reserve(values)(moved)
  let mut index: u64 = 0
  while(index < moved) {
    let item = unsafe {
      raw_take(raw_offset(other.pointer, index))
    }
    unsafe {
      raw_init(raw_offset(values.pointer, start + index), item)
    }
    index = index + 1
  }
  values.length = start + moved
  other.length = 0
}

/// Reallocates storage so capacity matches the current length.
let vec_shrink_to_fit = { <T: type>(values: Borrow<mut><Vec<T>>): () =>
    if(values.length != values.storage_capacity) {
    let new_pointer = vec_allocate<T>(values.length)
    let mut index: u64 = 0
    while(index < values.length) {
      let item = unsafe {
        raw_take(raw_offset(values.pointer, index))
      }
      unsafe {
        raw_init(raw_offset(new_pointer, index), item)
      }
      index = index + 1
    }
    vec_deallocate(values.pointer, values.storage_capacity)
    values.pointer = new_pointer
    values.storage_capacity = values.length
  }
}

/// Copies the element at `index` out of `values`.
let vec_read = { <T: type>(values: Borrow<Vec<T>>)(index: u64): T requires(T is Copyable) =>
    if(index >= values.length) {
    unsafe {
      raw_trap()
    }
  }
  unsafe {
    *raw_offset(values.pointer, index)
  }
}

/// Copies `value` into the element slot at `index`.
let vec_write = { <T: type>(values: Borrow<mut><Vec<T>>)(index: u64)(copy value: T): () requires(T is Copyable) =>
    if(index >= values.length) {
    unsafe {
      raw_trap()
    }
  }
  unsafe {
    *raw_offset(values.pointer, index) = value
  }
}

/// Provides inherent vector constructors and mutation operations.
extend(Vec<T>) {
  /// Creates an empty vector with zero capacity.
  let new = { (): Vec<T> =>  vec_new() }
  /// Creates an empty vector with storage for `capacity` elements.
  let with_capacity = { (capacity: u64): Vec<T> =>  vec_with_capacity(capacity) }
  /// Returns the number of initialized elements.
  let len = { (self: Borrow<self>)(): u64 =>  vec_len(self) }
  /// Borrows all initialized elements as a Slice.
  let as_slice = { <a: access = shared>(self: Borrow<a><self>)(): Borrow<a><Slice<T>> =>
      unsafe {
      raw_slice<a>(self.pointer, self.length, borrow<a>(self))
    }
  }
  /// Returns the number of elements that fit without reallocating.
  let capacity = { (self: Borrow<self>)(): u64 =>  vec_capacity(self) }
  /// Borrows the element at `index`, trapping if it is out of bounds.
  let get = { <a: access = shared>
      (self: Borrow<a><self>)
      (index: u64): Option<Borrow<a><T>> =>
      if(index >= self.length) {
      Option.None
    } else: {
      Option.Some(unsafe {
        raw_borrow<a>(raw_offset(self.pointer, index), borrow<a>(self))
      })
    }
  }
  /// Borrows the element at `index`, trapping if it is out of bounds.
  let at = { <a: access = shared>(self: Borrow<a><self>)(index: u64): Borrow<a><T> =>
      if(index >= self.length) {
      unsafe {
        raw_trap()
      }
    }
    unsafe {
      raw_borrow<a>(raw_offset(self.pointer, index), borrow<a>(self))
    }
  }
  /// Borrows the first element, or returns `None` when empty.
  let first = { <a: access = shared>
      (self: Borrow<a><self>)(): Option<Borrow<a><T>> =>
      self.get<a>(0)
  }
  /// Borrows the last element, or returns `None` when empty.
  let last = { <a: access = shared>
      (self: Borrow<a><self>)(): Option<Borrow<a><T>> =>
      let length = self.length
    if(length == 0) {
      Option.None
    } else: {
      self.get<a>(length - 1)
    }
  }
  /// Borrows the first element accepted by `predicate`.
  let find = { <e: effects>with<e>(self: Borrow<self>)(move predicate: with<e>(Borrow<T>) :bool): Option<Borrow<T>> =>
      let values = self.as_slice()
    values.find(predicate)
  }
  /// Returns the index of the first element accepted by `predicate`.
  let position = { <e: effects>with<e>(self: Borrow<self>)(move predicate: with<e>(Borrow<T>) :bool): Option<u64> =>
      let values = self.as_slice()
    values.position(predicate)
  }
  /// Returns whether any element is accepted by `predicate`.
  let any = { <e: effects>with<e>(self: Borrow<self>)(move predicate: with<e>(Borrow<T>) :bool): bool =>
      let values = self.as_slice()
    values.any(predicate)
  }
  /// Returns whether every element is accepted by `predicate`.
  let all = { <e: effects>with<e>(self: Borrow<self>)(move predicate: with<e>(Borrow<T>) :bool): bool =>
      let values = self.as_slice()
    values.all(predicate)
  }
  /// Folds elements from left to right into `initial`.
  let fold = { <e: effects, Accumulator: type>with<e>(self: Borrow<self>)(move initial: Accumulator)(move combine: with<e>(Accumulator, Borrow<T>) :Accumulator): Accumulator =>
      let values = self.as_slice()
    values.fold(initial)(combine)
  }
  /// Ensures capacity for at least `additional` more elements.
  let reserve = { (self: Borrow<mut><self>)(additional: u64): () =>  vec_reserve(self)(additional) }
  /// Appends `value` to the end of this vector.
  let push = { (self: Borrow<mut><self>)(value: T): () =>  vec_push(self)(value) }
  /// Replaces the element at `index` and returns the previous element.
  let replace = { (self: Borrow<mut><self>)(index: u64)(value: T): T =>  vec_replace(self)(index)(value) }
  /// Removes and returns the last element, or `None` if empty.
  let pop = { (self: Borrow<mut><self>)(): Option<T> =>  vec_pop(self) }
  /// Drops elements from the end until the length is at most `new_length`.
  let truncate = { (self: Borrow<mut><self>)(new_length: u64): () =>  vec_truncate(self)(new_length) }
  /// Removes all elements from this vector.
  let clear = { (self: Borrow<mut><self>)(): () =>  vec_clear(self) }
  /// Returns whether this vector has no initialized elements.
  let is_empty = { (self: Borrow<self>)(): bool =>  vec_is_empty(self) }
  /// Removes an element by replacing it with the last element.
  let swap_remove = { (self: Borrow<mut><self>)(index: u64): T =>  vec_swap_remove(self)(index) }
  /// Swaps the elements at `left` and `right`.
  let swap = { (self: Borrow<mut><self>)(left: u64, right: u64): () =>  vec_swap(self)(left: left, right: right) }
  /// Reverses the initialized elements in place.
  let reverse = { (self: Borrow<mut><self>)(): () =>  vec_reverse(self) }
  /// Inserts `value` at `index`, shifting later elements right.
  let insert = { (self: Borrow<mut><self>)(index: u64)(value: T): () =>  vec_insert(self)(index)(value) }
  /// Removes and returns the element at `index`, shifting later elements left.
  let remove = { (self: Borrow<mut><self>)(index: u64): T =>  vec_remove(self)(index) }
  /// Moves all elements from `other` onto the end of this vector.
  let append = { (self: Borrow<mut><self>)(other: Borrow<mut><Vec<T>>): () =>  vec_append(self)(other) }
  /// Replaces this vector with an empty one and returns its previous allocation.
  let take = { (self: Borrow<mut><self>)(): Vec<T> =>
      let previous = Vec<T> { pointer: self.pointer, length: self.length, storage_capacity: self.storage_capacity }
    self.pointer = vec_allocate(0)
    self.length = 0
    self.storage_capacity = 0
    previous
  }
  /// Reallocates storage so capacity matches the current length.
  let shrink_to_fit = { (self: Borrow<mut><self>)(): () =>  vec_shrink_to_fit(self) }
}

/// Provides copy-based Slice extension and mutation operations.
extend(Vec<T>)<requires: T is Copyable> {
  /// Copies every element of `source` onto the end of this vector.
  let extend_from_slice = { (self: Borrow<mut><self>)(source: Borrow<Slice<T>>): () =>
      let additional = source.len()
    vec_reserve(self)(additional)
    if(additional > 0) {
      let source_values = unsafe {
        raw_slice_ptr(source)
      }
      let mut index: u64 = 0
      while(index < additional) {
        let value = unsafe {
          *raw_offset(source_values, index)
        }
        unsafe {
          raw_init(raw_offset(self.pointer, self.length), value)
        }
        self.length = self.length + 1
        index = index + 1
      }
    }
  }
  /// Copies the element at `index` out of this vector.
  let read = { (self: Borrow<self>)(index: u64): T =>  vec_read(self)(index) }
  /// Copies `value` into the element slot at `index`.
  let write = { (self: Borrow<mut><self>)(index: u64)(copy value: T): () =>  vec_write(self)(index)(value) }
  /// Replaces every initialized element with a copy of `value`.
  let fill = { (self: Borrow<mut><self>)(copy value: T): () =>
      let values = self.as_slice<mut>()
    values.fill(value)
  }
  /// Copies an equally sized source Slice into the initialized elements.
  let copy_from = { (self: Borrow<mut><self>)(source: Borrow<Slice<T>>): () =>
      let values = self.as_slice<mut>()
    values.copy_from(source)
  }
  /// Copies an initialized range within this vector with overlap-safe semantics.
  let copy_within = { (self: Borrow<mut><self>)
      (source_start: u64, source_end: u64, destination_start: u64): () =>
      let values = self.as_slice<mut>()
    values.copy_within(source_start, source_end, destination_start)
  }
}

/// Owning Iterator over a vector.
pub let VecIntoIter = <T: type> struct {
  pointer: Ptr<mut><T>,
  next_index: u64,
  length: u64,
  storage_capacity: u64,
}

/// Routes bracket access through the source-defined indexing protocol.
extend(Vec<T>, Index<u64>) {
  let Output = T
  let index = { <a: access>
      (self: Borrow<a><self>)
      (key: u64): Borrow<a><T> =>
      self.at<a>(key)
  }
}

/// Advances an owning vector Iterator in source order.
extend(VecIntoIter<T>, Iterator) {
  let Item = OwnedItem<T>;
  let next = { <r: region>(self: Borrow<mut><r><self>)(): Option<T> =>
      if(self.next_index == self.length) {
      Option.None
    } else: {
      let value = unsafe {
        raw_take(raw_offset(self.pointer, self.next_index))
      }
      self.next_index = self.next_index + 1
      Option.Some(value)
    }
  }
}

/// Consumes a vector into an owning Iterator.
extend(Vec<T>, IntoIterator) {
  let Iter = VecIntoIter<T>;
  let into_iter = { (move self)(): VecIntoIter<T> =>
      let iterator = VecIntoIter<T> { pointer: self.pointer, next_index: 0, length: self.length, storage_capacity: self.storage_capacity }
    forget(self)
    iterator
  }
}

/// Drops elements not yet yielded and releases the transferred vector storage.
extend(VecIntoIter<T>, Droppable) {
  let drop = { (self: Borrow<mut><self>)(): () =>
      while(self.next_index < self.length) {
      let item = unsafe {
        raw_take(raw_offset(self.pointer, self.next_index))
      }
      self.next_index = self.next_index + 1
    }
    vec_deallocate(self.pointer, self.storage_capacity)
  }
}

/// Drops initialized elements and releases vector storage.
extend(Vec<T>, Droppable) {
  /// Drops all initialized elements and deallocates storage.
  let drop = { (self: Borrow<mut><self>)(): () =>
      let mut index: u64 = 0
    while(index < self.length) {
      let item = unsafe {
        raw_take(raw_offset(self.pointer, index))
      }
      index = index + 1
    }
    vec_deallocate(self.pointer, self.storage_capacity)
  }
}

/// Rebuilds vector ownership from initialized storage supplied by another
/// adapter in this package.
pub(package) let vec_from_raw_parts = { <T: type>with<core.unsafe.unsafety>(pointer: Ptr<mut><T>, length: u64, capacity: u64): Vec<T> =>
    Vec<T> { pointer: pointer, length: length, storage_capacity: capacity }
}

/// Consumes a vector and transfers its allocation to another package adapter.
pub(package) let vec_into_raw_parts = { <T: type>
    (move values: Vec<T>): (Ptr<mut><T>, u64, u64) =>
    let parts = (values.pointer, values.length, values.storage_capacity)
  forget(values)
  parts
}

/// Provides equality-based membership for vectors.
extend(Vec<T>)<requires: T is Copyable && T is core.cmp.Eq<T>> {
  /// Returns whether this vector contains an element equal to `needle`.
  let contains = { (self: Borrow<self>)(copy needle: T): bool =>
      let values = self.as_slice()
    values.contains(needle)
  }
}

/// Owning heap allocation for a single value of type `T`.
pub let Box<T: type> = struct {
  /// Raw pointer to the initialized heap slot owned by this Box.
  pointer: Ptr<mut><T>,
}

/// Allocates heap storage and moves `value` into a new Box.
let box_new<T: type>(value: T): Box<T> = {
  let pointer = unsafe {
    raw_alloc<T>(size_of<T>, align_of<T>)
  }
  unsafe {
    raw_init(pointer, value)
  }
  Box<T>{ pointer: pointer }
}

/// Consumes `boxed` without deallocating and returns its owned raw pointer.
let box_into_raw<T: type>(move boxed: Box<T>): Ptr<mut><T> = {
  let pointer = boxed.pointer
  forget(boxed)
  pointer
}

/// Copies the boxed value out of `boxed`.
let box_read<T: type>(boxed: Borrow<Box<T>>): T = requires(T is Copyable) {
  unsafe {
    *boxed.pointer
  }
}

/// Copies `value` over the current boxed value.
let box_write<T: type>(boxed: Borrow<mut><Box<T>>)(copy value: T): () = requires(T is Copyable) {
  unsafe {
    *boxed.pointer = value
  }
}

/// Consumes `boxed`, deallocates its storage, and returns the owned value.
let box_into_inner<T: type>(move boxed: Box<T>): T = {
  let pointer = boxed.pointer
  let value = unsafe {
    raw_take(pointer)
  }
  unsafe {
    raw_dealloc(pointer, size_of<T>, align_of<T>)
  }
  forget(boxed)
  value
}

/// Replaces the boxed value and returns the previous value.
let box_replace<T: type>(boxed: Borrow<mut><Box<T>>)(value: T): T = {
  let pointer = boxed.pointer
  let previous = unsafe {
    raw_take(pointer)
  }
  unsafe {
    raw_init(pointer, value)
  }
  previous
}

/// Borrows the boxed value with the same access and region as `boxed`.
let box_as_ref<a: access, r: region, T: type>
  (boxed: Borrow<a><r><Box<T>>): Borrow<a><r><T> = {
  unsafe {
    raw_borrow<a>(boxed.pointer, borrow<a>(boxed))
  }
}

/// Provides inherent constructors and accessors for `Box`.
extend(Box<T>) {
  /// Allocates a new Box containing `value`.
  let new(value: T): Box<T> = { box_new(value) }
  /// Rebuilds unique ownership from a pointer returned by `Box.into_raw`.
  let from_raw: with<core.unsafe.unsafety>(pointer: Ptr<mut><T>): Box<T> = {
    Box<T>{ pointer: pointer }
  }
  /// Borrows the boxed value with the requested access.
  let as_ref<a: access>(self: Borrow<a><self>)(): Borrow<a><T> = {
    unsafe {
      raw_borrow<a>(self.pointer, borrow<a>(self))
    }
  }
  /// Consumes this Box and returns its owned value.
  let into_inner(move self)(): T = { box_into_inner(self) }
  /// Consumes this Box without deallocating and returns its owned raw pointer.
  let into_raw(move self)(): Ptr<mut><T> = { box_into_raw(self) }
  /// Replaces the boxed value and returns the previous value.
  let replace(self: Borrow<mut><self>)(value: T): T = { box_replace(self)(value) }
}

/// Provides copy-only value accessors for `Box`.
extend(Box<T>)<requires: T is Copyable> {
  /// Copies the boxed value out of this Box.
  let read(self: Borrow<self>)(): T = { box_read(self) }
  /// Copies `value` over the current boxed value.
  let write(self: Borrow<mut><self>)(copy value: T): () = { box_write(self)(value) }
}

/// Releases one Box allocation after its value has been taken.
let box_deallocate<T: type>(pointer: Ptr<mut><T>): () = {
  unsafe {
    raw_dealloc(pointer, size_of<T>, align_of<T>)
  }
}

/// Drops the owned value and releases its heap allocation.
extend(Box<T>, Droppable) {
  let drop(self: Borrow<mut><self>)(): () = {
    let pointer = self.pointer
    do {
      let value = unsafe {
        raw_take(pointer)
      }
    }
    box_deallocate(pointer)
  }
}

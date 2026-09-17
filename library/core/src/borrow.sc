// Borrow access, type, and value contracts.
/// Describes whether a Borrow is shared or mutable.
pub let access = sort(1) {
  /// Shared read-only access.
  shared
  /// Exclusive mutable access.
  mut
}

/// Unqualified alias for `access.mut`.
pub let mut = access.mut
/// Unqualified alias for `access.shared`.
pub let shared = access.shared

/// Type constructor for a Borrow with access `A`, region `R`, and pointee `T`.
pub let Borrow<a: access = shared>
  <r: region>
  <T: type>: type = builtin()

/// Creates or reborrows a Borrow of an addressable pointee.
pub let borrow<a: access = shared>
  <r: region>
  <T: type>
  (value: T): Borrow<a><r><T> = builtin()

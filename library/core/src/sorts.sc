// Compile-time sorts used by generic parameters and calling conventions.
/// Constructs the universe at `level`. Universe levels start at one.
pub let sort<level: usize>: sort<level + 1> = builtin()

/// Returns the classifier of a compile-time value.
pub let sort_of<
  level: usize,
  classifier: sort<level>,
  value: classifier,
>: sort<level> = builtin()

/// Returns the runtime type of an unevaluated expression.
pub let type_of<T: type>(move expression: (): T): type = builtin()

/// Sort of compile-time type values.
pub let type: sort<2>
/// Sort of compile-time lifetime regions.
pub let region: sort<2>
/// Sort of individual compile-time effect identities.
pub let effect: sort<2>
/// Sort of normalized compile-time effect rows.
pub let effects: sort<2>
/// Sort of compile-time schemas expanded into one runtime parameter group.
pub let parameters: sort<2>
/// Sort of trait requirements consumed by compile-time constraint queries.
pub let constraint: sort<2>

/// Compile-time relation between values classified by sorts.
pub let Is<right: sort<2>> = trait<self: sort<2>> {
          is<left: self, right: right>: bool
        }

        extend<type, Is<constraint>> {
              let is<
                Left: type,
                right: constraint,
              >: bool = builtin()
            }

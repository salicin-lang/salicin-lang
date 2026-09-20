let identity = <m: <p: parameters>: parameters, t: type>(m value: t): t => { value }

let main = (): i32 => { identity<m: shared, t: i32>(42) }

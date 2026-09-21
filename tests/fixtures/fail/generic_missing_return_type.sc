let identity: <t: type>(move value: t) = { value }

let main: (): i32 = { identity<i32>(42) }

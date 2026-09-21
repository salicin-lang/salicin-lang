let Option = core.Option

let boxed = struct { value: i32 }

let main(): i32 = { Option<boxed>.Some(boxed { value: 42 })?.missing() ?? 0 }

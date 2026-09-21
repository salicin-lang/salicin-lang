let invalid<t: type> = struct { next: invalid<t> }

let main(): i32 = { 42 }

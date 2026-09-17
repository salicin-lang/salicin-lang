let cell<t: type> = struct { value: t }
let family<t: type>: type = cell(t)

let main(value: family): i32 = { 0 }

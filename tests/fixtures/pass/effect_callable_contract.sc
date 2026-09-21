let EffectCallable = core.effect.EffectCallable

let abandon: (move action: EffectCallable<i32, i32, i32>): () = { () }

let main: (): i32 = { 42 }

test("effect_callable_contract.sc") {
  std.test.assert(main() == 42)
}

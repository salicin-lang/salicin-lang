let main: (): i32 = {
  let future = async {
    await async { 42 }
  }
  0
}

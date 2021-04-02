{
  val loadCount = AttributeKey[Int]("load-count")
  val unloadCount = AttributeKey[Int]("unload-count")
  def f(key: AttributeKey[Int]) = (s: State) => {
    val previous = s get key getOrElse 0
    s.put(key, previous + 1)
  }
  Seq(
    onLoad in Global ~= (f(loadCount) compose _),
    onUnload in Global ~= (f(unloadCount) compose _)
  )
}

InputKey[Unit]("checkCount") := {
  val s = state.value
  val args = Def.spaceDelimited().parsed
  def get(label: String) = s get AttributeKey[Int](label) getOrElse 0
  val loadCount = get("load-count")
  val unloadCount = get("unload-count")
  val expectedLoad = args(0).toInt
  val expectedUnload = args(1).toInt
  assert(expectedLoad == loadCount, s"Expected load count: $expectedLoad, got: $loadCount")
  assert(expectedUnload == unloadCount, s"Expected unload count: $expectedUnload, got: $unloadCount")
}

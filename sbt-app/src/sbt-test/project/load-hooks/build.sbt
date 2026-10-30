{
  val loadCount = AttributeKey[Int]("load-count")
  val unloadCount = AttributeKey[Int]("unload-count")
  def f(key: AttributeKey[Int]) = (s: State) => {
    val previous = s.get(key)getOrElse(0)
    s.put(key, previous + 1)
  }
  Seq(
    Global / onLoad ~= (f(loadCount) compose _),
    Global / onUnload ~= (f(unloadCount) compose _)
  )
}

InputKey[Unit]("checkCount") := {
  val s = state.value
  val args = Def.spaceDelimited().parsed
  def get(label: String) = s.get(AttributeKey[Int](label)).getOrElse(0)
  val loadCount = get("load-count")
  val unloadCount = get("unload-count")
  val expectedLoad = args(0).toInt
  val expectedUnload = args(1).toInt
  assert(expectedLoad == loadCount, s"Expected load count: $expectedLoad, got: $loadCount")
  assert(expectedUnload == unloadCount, s"Expected unload count: $expectedUnload, got: $unloadCount")
}

Global / onUnload := {
  val previous = (Global / onUnload).value
  val log = (LocalRootProject / baseDirectory).value / "unloads"
  s =>
    IO.append(log, "unload\n")
    previous(s)
}

InputKey[Unit]("checkUnloads") := {
  val expected = Def.spaceDelimited().parsed.head.toInt
  val log = baseDirectory.value / "unloads"
  val actual = if log.exists then IO.readLines(log).size else 0
  assert(expected == actual, s"Expected the unload hook to run $expected times, got: $actual")
}

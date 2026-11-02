import Tasks.*

Global / localCacheDirectory := baseDirectory.value / "diskcache"

count1 := bump(target.value / "count1.txt")

Tasks.scalaSettings

check := Def.uncached {
  val Seq(name, n) = Def.spaceDelimited().parsed
  val lines = IO.readLines(target.value / s"$name.txt")
  assert(lines.size == n.toInt, s"$name ran ${lines.size} times (expected $n)")
}

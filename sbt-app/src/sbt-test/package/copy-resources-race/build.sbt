sbtPlugin := true

def stagingFiles(dir: File): Seq[File] = (dir ** "large.bin.*.tmp").get()

def waitUntil(timeoutMs: Long)(cond: => Boolean): Unit =
  val deadline = System.currentTimeMillis + timeoutMs
  while !cond && System.currentTimeMillis < deadline do Thread.sleep(1)
  assert(cond, "timed out waiting for the resource copy")

Compile / resourceGenerators += Def.task {
  val f = (Compile / resourceManaged).value / "large.bin"
  IO.write(f, new Array[Byte](64 << 20))
  Seq(f)
}.taskValue

Compile / products := Def.uncached {
  val ps = (Compile / products).value
  val dir = (Compile / classDirectory).value
  waitUntil(10000)(stagingFiles(dir).nonEmpty)
  ps
}

Compile / packageInternal / mappings := Def.uncached {
  val ms = (Compile / packageInternal / mappings).value
  val dir = (Compile / classDirectory).value
  waitUntil(10000)(stagingFiles(dir).isEmpty)
  ms
}

import sbt.internal.util.StringVirtualFile1
import sjsonnew.BasicJsonProtocol.*

val pure1 = taskKey[Unit]("")
val map1 = taskKey[String]("")
val mapN1 = taskKey[Unit]("")

Global / localCacheDirectory := new File("/tmp/sbt/diskcache/")

pure1 := (Def.cachedTask {
  val output = StringVirtualFile1("a.txt", "foo")
  Def.declareOutput(output)
  ()
}).value

map1 := (Def.cachedTask {
  pure1.value
  val output1 = StringVirtualFile1("b1.txt", "foo")
  val output2 = StringVirtualFile1("b2.txt", "foo")
  Def.declareOutput(output1)
  Def.declareOutput(output2)
  "something"
}).value

mapN1 := (Def.cachedTask {
  pure1.value
  map1.value
  val output = StringVirtualFile1("c.txt", "foo")
  Def.declareOutput(output)
  ()
}).value

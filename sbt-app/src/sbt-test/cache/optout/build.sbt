import sbt.internal.util.StringVirtualFile1
import sjsonnew.BasicJsonProtocol.*
import CustomKeys.*

Global / localCacheDirectory := baseDirectory.value / "diskcache"

aa := A()

// This tests that aa is opt'ed out from caching
map1 := (Def.cachedTask {
  aa.value
  val output1 = StringVirtualFile1("target/out/b1.txt", "foo")
  val output2 = StringVirtualFile1("target/out/b2.txt", "foo")
  Def.declareOutput(output1)
  Def.declareOutput(output2)
  "something"
}).value

mapN1 := (Def.cachedTask {
  aa.value
  map1.value
  val output = StringVirtualFile1("target/out/c.txt", "foo")
  Def.declareOutput(output)
  ()
}).value

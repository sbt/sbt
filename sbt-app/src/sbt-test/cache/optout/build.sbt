import sbt.internal.util.StringVirtualFile1
import sjsonnew.BasicJsonProtocol.*
import CustomKeys.*

Global / localCacheDirectory := new File("/tmp/sbt/diskcache/")

aa := A()

// This tests that pure1 is opt'ed out from caching
map1 := (Def.cachedTask {
  aa.value
  val output1 = StringVirtualFile1("b1.txt", "foo")
  val output2 = StringVirtualFile1("b2.txt", "foo")
  Def.declareOutput(output1)
  Def.declareOutput(output2)
  "something"
}).value

mapN1 := (Def.cachedTask {
  aa.value
  map1.value
  val output = StringVirtualFile1("c.txt", "foo")
  Def.declareOutput(output)
  ()
}).value

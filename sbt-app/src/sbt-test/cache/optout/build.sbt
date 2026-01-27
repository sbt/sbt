import sbt.internal.util.StringVirtualFile1
import sjsonnew.BasicJsonProtocol.*
import CustomKeys.*

Global / localCacheDirectory := baseDirectory.value / "diskcache"

aa := Def.uncached(A())

// This tests that aa is opted out from caching
map1 := {
  aa.value
  val output1 = StringVirtualFile1("${OUT}/b1.txt", "foo")
  val output2 = StringVirtualFile1("${OUT}/b2.txt", "foo")
  Def.declareOutput(output1)
  Def.declareOutput(output2)
  "something"
}

mapN1 := {
  aa.value
  map1.value
  val output = StringVirtualFile1("${OUT}/c.txt", "foo")
  Def.declareOutput(output)
  ()
}

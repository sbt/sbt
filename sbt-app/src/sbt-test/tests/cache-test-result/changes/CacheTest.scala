import java.io.File

import org.scalatest.FunSuite

class CacheTest extends FunSuite {
  test("record changed execution") {
    val count = Iterator.from(1).find(i => !new File(s"run-$i").exists).get
    assert(new File(s"run-$count").createNewFile())
  }
}

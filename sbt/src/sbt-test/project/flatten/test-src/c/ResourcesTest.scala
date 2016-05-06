package d

  import org.scalacheck._
  import Prop._

class ResourcesTest extends Properties("Resources") {
  property("load main resources ok") = forAll( (a: Boolean) => { b.ScalaC.loadResources(); true })
  property("load test resources ok") = forAll( (a: Boolean) => { ScalaD.loadResources(); true })
}

object ScalaD {
  def loadResources(): Unit = {
    resource("/test-resource")
    resource("/c/test-resource-c")
  }
  def resource(s: String): Unit =
    assert(getClass.getResource(s) != null, s"Could not find resource '$s'")
}

package b

class ScalaB {
  def decrement(i: Int) = i - 1
}
object ScalaC
{
  def loadResources(): Unit = {
    resource("/main-resource")
    resource("/a/main-resource-a")
  }
  def resource(s: String): Unit =
    assert(getClass.getResource(s) != null, "Could not find resource '" + s + "'")
}

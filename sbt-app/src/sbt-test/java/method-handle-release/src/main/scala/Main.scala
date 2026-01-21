import java.lang.invoke.{ MethodHandle, MethodHandles }

object Main {
  case class TestClass(number: Int)
  def testMethod(): TestClass = {
    TestClass(10)
  }

  def wrappedMethod(handle: MethodHandle): TestClass = {
    handle.invokeExact()
  }

  def main(args: Array[String]): Unit = {
    val method = classOf[Main.type].getDeclaredMethod("testMethod")
    method.setAccessible(true)
    val handle = MethodHandles.lookup.unreflect(method)
    println(wrappedMethod(handle))
  }
}

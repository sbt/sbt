import munit.FunSuite

class ResourceTest extends FunSuite:
  test("Test resource on test classpath"):
    assert(getClass.getResource("TestResource.txt") != null)

  test("Test resource with slash on test classpath"):
    assert(getClass.getResource("/TestResource.txt") != null)

  test("Test resource contents"):
    val str = new String(
      getClass.getResourceAsStream("/TestResource.txt").readAllBytes()
    )
    assert(str == "Success")

  test("Main resource on test classpath"):
    assert(getClass.getResource("MainResource.txt") != null)

  test("Main resource with slash on test classpath"):
    assert(getClass.getResource("/MainResource.txt") != null)

  test("Main resource contents"):
    val str = new String(
      getClass.getResourceAsStream("/MainResource.txt").readAllBytes()
    )
    assert(str == "Main")
end ResourceTest

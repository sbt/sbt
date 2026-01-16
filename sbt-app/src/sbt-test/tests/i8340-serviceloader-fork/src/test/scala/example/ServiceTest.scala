package example

import org.scalatest.funsuite.AnyFunSuite
import java.util.ServiceLoader
import scala.jdk.CollectionConverters._

class ServiceTest extends AnyFunSuite {
  test("ServiceLoader should find service implementation when Test / fork := true") {
    val services = ServiceLoader.load(classOf[Service])
    val iterator = services.iterator()
    assert(iterator.hasNext, "ServiceLoader should find at least one service")
    val service = iterator.next()
    assert(service.name == "MyServiceImpl", s"Expected service name 'MyServiceImpl', got '${service.name}'")
  }
}


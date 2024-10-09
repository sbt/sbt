import java.io.File
import java.nio.file.Files

import scala.util.Try

object Main extends App {

  def classFound(clsName: String) = Try(
    Thread.currentThread()
      .getContextClassLoader()
      .loadClass(clsName)
  ).toOption.nonEmpty

  val name = "org.jclouds.openstack.nova.functions.ParseServerFromJsonResponseTest"
  val classifierTest = classFound(name)

  assert(classifierTest, s"Couldn't find $name")
}

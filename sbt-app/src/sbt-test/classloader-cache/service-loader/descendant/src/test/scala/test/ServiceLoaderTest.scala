package test

import collection.JavaConverters._
import org.scalatest._

class ServiceLoaderTest extends FlatSpec {
  val expected = Set(classOf[dependency.Runnable], classOf[descendant.Runnable])

  val descendantClassLoader = classOf[descendant.Runnable].getClassLoader
  val descendantRunnableLoader = java.util.ServiceLoader.load(classOf[java.lang.Runnable], descendantClassLoader)
  val descendantLoadedClasses = descendantRunnableLoader.iterator().asScala.map(_.getClass).toSet
  assert(descendantLoadedClasses == expected)

  // this was the actual problem, when classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.AllLibraryJars
  val dependencyClassLoader = classOf[dependency.Runnable].getClassLoader
  val dependencyRunnableLoader = java.util.ServiceLoader.load(classOf[java.lang.Runnable], dependencyClassLoader)
  val dependencyLoadedClasses = dependencyRunnableLoader.iterator().asScala.map(_.getClass).toSet
  assert(dependencyLoadedClasses == expected)
}


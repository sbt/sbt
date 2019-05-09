package sbtprojectmatrix

import java.lang.reflect.InvocationTargetException
import scala.reflect.ClassTag
import scala.util.Try

object ReflectionUtil {
  def getSingletonObject[A: ClassTag](classLoader: ClassLoader, className: String): Try[A] =
    Try {
      val clazz = classLoader.loadClass(className)
      val t = implicitly[ClassTag[A]].runtimeClass
      Option(clazz.getField("MODULE$").get(null)) match {
        case None                        => throw new ClassNotFoundException(s"Unable to find $className using classloader: $classLoader")
        case Some(c) if !t.isInstance(c) => throw new ClassCastException(s"${clazz.getName} is not a subtype of $t")
        case Some(c: A)                  => c
      }
    }
    .recover {
      case i: InvocationTargetException if i.getTargetException != null => throw i.getTargetException
    }

  def objectExists(classLoader: ClassLoader, className: String): Boolean =
    try {
      classLoader.loadClass(className).getField("MODULE$").get(null) != null
    } catch {
      case _: Throwable => false
    }

  def withContextClassloader[A](loader: ClassLoader)(body: ClassLoader => A): A = {
    val current = Thread.currentThread().getContextClassLoader
    try {
      Thread.currentThread().setContextClassLoader(loader)
      body(loader)
    } finally Thread.currentThread().setContextClassLoader(current)
  }
}

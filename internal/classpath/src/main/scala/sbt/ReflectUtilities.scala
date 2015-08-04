/* sbt -- Simple Build Tool
 * Copyright 2008 David MacIver, Mark Harrah
 */
package sbt

import scala.collection._

object ReflectUtilities {
  /** Converts the camelCase String `name` to lowercase separated by `separator`. */
  def transformCamelCase(name: String, separator: Char): String =
    {
      val buffer = new StringBuilder
      for (char <- name) {
        import java.lang.Character._
        if (isUpperCase(char)) {
          buffer += separator
          buffer += toLowerCase(char)
        } else
          buffer += char
      }
      buffer.toString
    }

  def ancestry(clazz: Class[_]): List[Class[_]] =
    if (clazz == classOf[AnyRef] || !classOf[AnyRef].isAssignableFrom(clazz)) List(clazz)
    else clazz :: ancestry(clazz.getSuperclass);

  def fields(clazz: Class[_]) =
    mutable.OpenHashMap(ancestry(clazz).
      flatMap(_.getDeclaredFields).
      map(f => (f.getName, f)): _*)

  /**
   * Collects all `val`s of type `T` defined on value `self`.
   * The returned Map maps the name of each `val` to its value.
   * This depends on scalac implementation details to determine what is a `val` using only Java reflection.
   */
  def allValsC[T](self: AnyRef, clazz: Class[T]): immutable.SortedMap[String, T] =
    {
      var mappings = new immutable.TreeMap[String, T]
      val correspondingFields = fields(self.getClass)
      for (method <- self.getClass.getMethods) {
        if (method.getParameterTypes.isEmpty && clazz.isAssignableFrom(method.getReturnType)) {
          for (field <- correspondingFields.get(method.getName) if field.getType == method.getReturnType) {
            val value = method.invoke(self).asInstanceOf[T]
            if (value == null) throw new UninitializedVal(method.getName, method.getDeclaringClass.getName)
            mappings += ((method.getName, value))
          }
        }
      }
      mappings
    }

  /**
   * Collects all `val`s of type `T` defined on value `self`.
   * The returned Map maps the name of each `val` to its value.
   * This requires an available `Manifest` for `T` and depends on scalac implementation details to determine
   * what is a `val` using only Java reflection.
   */
  def allVals[T](self: AnyRef)(implicit mt: scala.reflect.Manifest[T]): immutable.SortedMap[String, T] =
    allValsC(self, mt.runtimeClass).asInstanceOf[immutable.SortedMap[String, T]]
}

/** An exception to indicate that while traversing the `val`s for an instance of `className`, the `val` named `valName` was `null`. */
final class UninitializedVal(val valName: String, val className: String) extends RuntimeException("val " + valName + " in class " + className + " was null.\nThis is probably an initialization problem and a 'lazy val' should be used.")
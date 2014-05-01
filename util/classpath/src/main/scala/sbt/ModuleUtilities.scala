/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

object ModuleUtilities {
  /**
   * Reflectively loads and returns the companion object for top-level class `className` from `loader`.
   * The class name should not include the `$` that scalac appends to the underlying jvm class for
   * a companion object.
   */
  def getObject(className: String, loader: ClassLoader): AnyRef =
    {
      val obj = Class.forName(className + "$", true, loader)
      val singletonField = obj.getField("MODULE$")
      singletonField.get(null)
    }

  def getCheckedObject[T](className: String, loader: ClassLoader)(implicit mf: reflect.ClassManifest[T]): T =
    mf.erasure.cast(getObject(className, loader)).asInstanceOf[T]

  def getCheckedObjects[T](classNames: Seq[String], loader: ClassLoader)(implicit mf: reflect.ClassManifest[T]): Seq[(String, T)] =
    classNames.map(name => (name, getCheckedObject(name, loader)))
}
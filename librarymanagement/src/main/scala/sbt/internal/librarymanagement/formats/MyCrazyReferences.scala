package sbt.internal.librarymanagement.formats

import scala.collection.mutable

object MyCrazyReferences {
  private val references: mutable.Map[String, Any] = mutable.Map.empty

  def apply[T](key: String, clazz: Class[T]): T = synchronized(clazz.cast(references(key)))

  def referenced[T](value: T): String = synchronized {
    val key = java.util.UUID.randomUUID.toString
    references(key) = value
    key
  }
}

/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** Configures how a module will be cross-versioned. */
abstract class CrossVersion() extends Serializable {




override def equals(o: Any): Boolean = o match {
  case x: CrossVersion => true
  case _ => false
}
override def hashCode: Int = {
  37 * (17 + "sbt.librarymanagement.CrossVersion".##)
}
override def toString: String = {
  "CrossVersion()"
}
}
object CrossVersion extends sbt.librarymanagement.CrossVersionFunctions {
  
}

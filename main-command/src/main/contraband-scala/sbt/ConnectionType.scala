/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt
sealed abstract class ConnectionType extends Serializable
object ConnectionType {
  
  /** This uses Unix domain socket on POSIX, and named pipe on Windows. */
  case object Local extends ConnectionType
  case object Tcp extends ConnectionType
}

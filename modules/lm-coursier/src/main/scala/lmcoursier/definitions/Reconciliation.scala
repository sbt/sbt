/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
sealed abstract class Reconciliation extends Serializable
object Reconciliation {
  
  
  case object Default extends Reconciliation
  case object Relaxed extends Reconciliation
}

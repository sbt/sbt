/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Configures logging during an 'update'.  `level` determines the amount of other information logged.
 * `Full` is the default and logs the most.
 * `DownloadOnly` only logs what is downloaded.
 * `Quiet` only displays errors.
 * `Default` uses the current log level of `update` task.
 */
sealed abstract class UpdateLogging extends Serializable
object UpdateLogging {
  
  
  case object Full extends UpdateLogging
  case object DownloadOnly extends UpdateLogging
  case object Quiet extends UpdateLogging
  case object Default extends UpdateLogging
}

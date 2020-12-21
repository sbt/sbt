/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** sbt interface for an Ivy ssh-based repository (ssh and sftp).  Requires the Jsch library.. */
abstract class SshBasedRepository(
  name: String,
  patterns: sbt.librarymanagement.Patterns,
  val connection: sbt.librarymanagement.SshConnection) extends sbt.librarymanagement.PatternsBasedRepository(name, patterns) with sbt.librarymanagement.SshBasedRepositoryExtra with Serializable {
  
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SshBasedRepository => (this.name == x.name) && (this.patterns == x.patterns) && (this.connection == x.connection)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.SshBasedRepository".##) + name.##) + patterns.##) + connection.##)
  }
  override def toString: String = {
    "SshBasedRepository(" + name + ", " + patterns + ", " + connection + ")"
  }
}
object SshBasedRepository {
  
}

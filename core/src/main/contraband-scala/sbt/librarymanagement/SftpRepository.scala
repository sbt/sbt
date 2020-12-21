/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** sbt interface for an Ivy repository over sftp.  More convenient construction is done using Resolver.sftp. */
final class SftpRepository private (
  name: String,
  patterns: sbt.librarymanagement.Patterns,
  connection: sbt.librarymanagement.SshConnection) extends sbt.librarymanagement.SshBasedRepository(name, patterns, connection) with sbt.librarymanagement.SftpRepositoryExtra with Serializable {
  def this(name: String, connection: sbt.librarymanagement.SshConnection, patterns: sbt.librarymanagement.Patterns) =
  this(name, patterns, connection)
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SftpRepository => (this.name == x.name) && (this.patterns == x.patterns) && (this.connection == x.connection)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.SftpRepository".##) + name.##) + patterns.##) + connection.##)
  }
  override def toString: String = {
    "SftpRepository(" + name + ", " + patterns + ", " + connection + ")"
  }
  private[this] def copy(name: String = name, patterns: sbt.librarymanagement.Patterns = patterns, connection: sbt.librarymanagement.SshConnection = connection): SftpRepository = {
    new SftpRepository(name, patterns, connection)
  }
  def withName(name: String): SftpRepository = {
    copy(name = name)
  }
  def withPatterns(patterns: sbt.librarymanagement.Patterns): SftpRepository = {
    copy(patterns = patterns)
  }
  def withConnection(connection: sbt.librarymanagement.SshConnection): SftpRepository = {
    copy(connection = connection)
  }
}
object SftpRepository {
  def apply(name: String, connection: sbt.librarymanagement.SshConnection, patterns: sbt.librarymanagement.Patterns) =
  new SftpRepository(name, patterns, connection)
  def apply(name: String, patterns: sbt.librarymanagement.Patterns, connection: sbt.librarymanagement.SshConnection): SftpRepository = new SftpRepository(name, patterns, connection)
}

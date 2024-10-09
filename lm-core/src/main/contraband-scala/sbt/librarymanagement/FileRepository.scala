/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** sbt interface for an Ivy filesystem repository.  More convenient construction is done using Resolver.file. */
final class FileRepository private (
  name: String,
  patterns: sbt.librarymanagement.Patterns,
  val configuration: sbt.librarymanagement.FileConfiguration) extends sbt.librarymanagement.PatternsBasedRepository(name, patterns) with Serializable {
  def this(name: String, configuration: sbt.librarymanagement.FileConfiguration, patterns: sbt.librarymanagement.Patterns) =
  this(name, patterns, configuration)
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: FileRepository => (this.name == x.name) && (this.patterns == x.patterns) && (this.configuration == x.configuration)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.FileRepository".##) + name.##) + patterns.##) + configuration.##)
  }
  override def toString: String = {
    "FileRepository(" + name + ", " + patterns + ", " + configuration + ")"
  }
  private[this] def copy(name: String = name, patterns: sbt.librarymanagement.Patterns = patterns, configuration: sbt.librarymanagement.FileConfiguration = configuration): FileRepository = {
    new FileRepository(name, patterns, configuration)
  }
  def withName(name: String): FileRepository = {
    copy(name = name)
  }
  def withPatterns(patterns: sbt.librarymanagement.Patterns): FileRepository = {
    copy(patterns = patterns)
  }
  def withConfiguration(configuration: sbt.librarymanagement.FileConfiguration): FileRepository = {
    copy(configuration = configuration)
  }
}
object FileRepository {
  def apply(name: String, configuration: sbt.librarymanagement.FileConfiguration, patterns: sbt.librarymanagement.Patterns) =
  new FileRepository(name, patterns, configuration)
  def apply(name: String, patterns: sbt.librarymanagement.Patterns, configuration: sbt.librarymanagement.FileConfiguration): FileRepository = new FileRepository(name, patterns, configuration)
}

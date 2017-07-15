/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class URLRepository private (
  name: String,
  patterns: sbt.librarymanagement.Patterns) extends sbt.librarymanagement.PatternsBasedRepository(name, patterns) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: URLRepository => (this.name == x.name) && (this.patterns == x.patterns)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.URLRepository".##) + name.##) + patterns.##)
  }
  override def toString: String = {
    "URLRepository(" + name + ", " + patterns + ")"
  }
  protected[this] def copy(name: String = name, patterns: sbt.librarymanagement.Patterns = patterns): URLRepository = {
    new URLRepository(name, patterns)
  }
  def withName(name: String): URLRepository = {
    copy(name = name)
  }
  def withPatterns(patterns: sbt.librarymanagement.Patterns): URLRepository = {
    copy(patterns = patterns)
  }
}
object URLRepository {
  
  def apply(name: String, patterns: sbt.librarymanagement.Patterns): URLRepository = new URLRepository(name, patterns)
}

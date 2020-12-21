/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.librarymanagement
final class SemSelAndChunk private (
  val comparators: Seq[sbt.internal.librarymanagement.SemComparator]) extends Serializable {
  def matches(version: sbt.librarymanagement.VersionNumber): Boolean = comparators.forall(_.matches(version))
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SemSelAndChunk => (this.comparators == x.comparators)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.librarymanagement.SemSelAndChunk".##) + comparators.##)
  }
  override def toString: String = {
    comparators.map(_.toString).mkString(" ")
  }
  private[this] def copy(comparators: Seq[sbt.internal.librarymanagement.SemComparator] = comparators): SemSelAndChunk = {
    new SemSelAndChunk(comparators)
  }
  def withComparators(comparators: Seq[sbt.internal.librarymanagement.SemComparator]): SemSelAndChunk = {
    copy(comparators = comparators)
  }
}
object SemSelAndChunk extends sbt.internal.librarymanagement.SemSelAndChunkFunctions {
  def apply(andClauseToken: String): SemSelAndChunk = parse(andClauseToken)
  def apply(comparators: Seq[sbt.internal.librarymanagement.SemComparator]): SemSelAndChunk = new SemSelAndChunk(comparators)
}

/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/**
 * Work around the inadequacy of Ivy's ArtifactTypeFilter (that it cannot reverse a filter)
 * @param types Represents the artifact types that we should try to resolve for (as in the allowed values of
                `artifact[type]` from a dependency `<publications>` section). One can use this to filter
                source / doc artifacts.
 * @param inverted Whether to invert the types filter (i.e. allow only types NOT in the set)
 */
final class ArtifactTypeFilter private (
  val types: Set[String],
  val inverted: Boolean) extends sbt.librarymanagement.ArtifactTypeFilterExtra with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ArtifactTypeFilter => (this.types == x.types) && (this.inverted == x.inverted)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.ArtifactTypeFilter".##) + types.##) + inverted.##)
  }
  override def toString: String = {
    "ArtifactTypeFilter(" + types + ", " + inverted + ")"
  }
  private[this] def copy(types: Set[String] = types, inverted: Boolean = inverted): ArtifactTypeFilter = {
    new ArtifactTypeFilter(types, inverted)
  }
  def withTypes(types: Set[String]): ArtifactTypeFilter = {
    copy(types = types)
  }
  def withInverted(inverted: Boolean): ArtifactTypeFilter = {
    copy(inverted = inverted)
  }
}
object ArtifactTypeFilter extends sbt.librarymanagement.ArtifactTypeFilterFunctions {
  
  def apply(types: Set[String], inverted: Boolean): ArtifactTypeFilter = new ArtifactTypeFilter(types, inverted)
}

/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package lmcoursier.definitions
/**
 * @param exclude Use "*" in either organization or name to match any.
 * @param include Use "*" in either organization or name to match any.
 */
final class ModuleMatchers private (
  val exclude: Set[lmcoursier.definitions.Module],
  val include: Set[lmcoursier.definitions.Module]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ModuleMatchers => (this.exclude == x.exclude) && (this.include == x.include)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "lmcoursier.definitions.ModuleMatchers".##) + exclude.##) + include.##)
  }
  override def toString: String = {
    "ModuleMatchers(" + exclude + ", " + include + ")"
  }
  private[this] def copy(exclude: Set[lmcoursier.definitions.Module] = exclude, include: Set[lmcoursier.definitions.Module] = include): ModuleMatchers = {
    new ModuleMatchers(exclude, include)
  }
  def withExclude(exclude: Set[lmcoursier.definitions.Module]): ModuleMatchers = {
    copy(exclude = exclude)
  }
  def withInclude(include: Set[lmcoursier.definitions.Module]): ModuleMatchers = {
    copy(include = include)
  }
}
object ModuleMatchers {
  /** ModuleMatchers that matches to any modules. */
  def all: ModuleMatchers = ModuleMatchers(Set.empty, Set.empty)
  def apply(exclude: Set[lmcoursier.definitions.Module], include: Set[lmcoursier.definitions.Module]): ModuleMatchers = new ModuleMatchers(exclude, include)
}

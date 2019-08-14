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
  val include: Set[lmcoursier.definitions.Module],
  val includeByDefault: Boolean) extends Serializable {
  
  private def this(exclude: Set[lmcoursier.definitions.Module], include: Set[lmcoursier.definitions.Module]) = this(exclude, include, true)
  
  override def equals(o: Any): Boolean = o match {
    case x: ModuleMatchers => (this.exclude == x.exclude) && (this.include == x.include) && (this.includeByDefault == x.includeByDefault)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "lmcoursier.definitions.ModuleMatchers".##) + exclude.##) + include.##) + includeByDefault.##)
  }
  override def toString: String = {
    "ModuleMatchers(" + exclude + ", " + include + ", " + includeByDefault + ")"
  }
  private[this] def copy(exclude: Set[lmcoursier.definitions.Module] = exclude, include: Set[lmcoursier.definitions.Module] = include, includeByDefault: Boolean = includeByDefault): ModuleMatchers = {
    new ModuleMatchers(exclude, include, includeByDefault)
  }
  def withExclude(exclude: Set[lmcoursier.definitions.Module]): ModuleMatchers = {
    copy(exclude = exclude)
  }
  def withInclude(include: Set[lmcoursier.definitions.Module]): ModuleMatchers = {
    copy(include = include)
  }
  def withIncludeByDefault(includeByDefault: Boolean): ModuleMatchers = {
    copy(includeByDefault = includeByDefault)
  }
}
object ModuleMatchers {
  /** ModuleMatchers that matches to any modules. */
  def all: ModuleMatchers = ModuleMatchers(Set.empty, Set.empty)
  def only(mod: Module): ModuleMatchers = ModuleMatchers(Set.empty, Set(mod), includeByDefault = false)
  def apply(exclude: Set[lmcoursier.definitions.Module], include: Set[lmcoursier.definitions.Module]): ModuleMatchers = new ModuleMatchers(exclude, include)
  def apply(exclude: Set[lmcoursier.definitions.Module], include: Set[lmcoursier.definitions.Module], includeByDefault: Boolean): ModuleMatchers = new ModuleMatchers(exclude, include, includeByDefault)
}

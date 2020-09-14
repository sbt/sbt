/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param compileProvider The languages the server supports compilation via method buildTarget/compile.
 * @param dependencySourcesProvider The server provides sources for library dependencies
                                    via method buildTarget/dependencySources
 * @param canReload Reloading the workspace state through workspace/reload is supported
 */
final class BuildServerCapabilities private (
  val compileProvider: Option[sbt.internal.bsp.CompileProvider],
  val dependencySourcesProvider: Option[Boolean],
  val canReload: Option[Boolean]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: BuildServerCapabilities => (this.compileProvider == x.compileProvider) && (this.dependencySourcesProvider == x.dependencySourcesProvider) && (this.canReload == x.canReload)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.BuildServerCapabilities".##) + compileProvider.##) + dependencySourcesProvider.##) + canReload.##)
  }
  override def toString: String = {
    "BuildServerCapabilities(" + compileProvider + ", " + dependencySourcesProvider + ", " + canReload + ")"
  }
  private[this] def copy(compileProvider: Option[sbt.internal.bsp.CompileProvider] = compileProvider, dependencySourcesProvider: Option[Boolean] = dependencySourcesProvider, canReload: Option[Boolean] = canReload): BuildServerCapabilities = {
    new BuildServerCapabilities(compileProvider, dependencySourcesProvider, canReload)
  }
  def withCompileProvider(compileProvider: Option[sbt.internal.bsp.CompileProvider]): BuildServerCapabilities = {
    copy(compileProvider = compileProvider)
  }
  def withCompileProvider(compileProvider: sbt.internal.bsp.CompileProvider): BuildServerCapabilities = {
    copy(compileProvider = Option(compileProvider))
  }
  def withDependencySourcesProvider(dependencySourcesProvider: Option[Boolean]): BuildServerCapabilities = {
    copy(dependencySourcesProvider = dependencySourcesProvider)
  }
  def withDependencySourcesProvider(dependencySourcesProvider: Boolean): BuildServerCapabilities = {
    copy(dependencySourcesProvider = Option(dependencySourcesProvider))
  }
  def withCanReload(canReload: Option[Boolean]): BuildServerCapabilities = {
    copy(canReload = canReload)
  }
  def withCanReload(canReload: Boolean): BuildServerCapabilities = {
    copy(canReload = Option(canReload))
  }
}
object BuildServerCapabilities {
  
  def apply(compileProvider: Option[sbt.internal.bsp.CompileProvider], dependencySourcesProvider: Option[Boolean], canReload: Option[Boolean]): BuildServerCapabilities = new BuildServerCapabilities(compileProvider, dependencySourcesProvider, canReload)
  def apply(compileProvider: sbt.internal.bsp.CompileProvider, dependencySourcesProvider: Boolean, canReload: Boolean): BuildServerCapabilities = new BuildServerCapabilities(Option(compileProvider), Option(dependencySourcesProvider), Option(canReload))
}

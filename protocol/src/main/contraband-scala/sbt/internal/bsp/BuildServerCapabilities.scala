/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param compileProvider The languages the server supports compilation via method buildTarget/compile.
 * @param testProvider The languages the server supports test execution via method buildTarget/test
 * @param dependencySourcesProvider The server provides sources for library dependencies
                                    via method buildTarget/dependencySources
 * @param canReload Reloading the workspace state through workspace/reload is supported
 */
final class BuildServerCapabilities private (
  val compileProvider: Option[sbt.internal.bsp.CompileProvider],
  val testProvider: Option[sbt.internal.bsp.TestProvider],
  val runProvider: Option[sbt.internal.bsp.RunProvider],
  val dependencySourcesProvider: Option[Boolean],
  val canReload: Option[Boolean]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: BuildServerCapabilities => (this.compileProvider == x.compileProvider) && (this.testProvider == x.testProvider) && (this.runProvider == x.runProvider) && (this.dependencySourcesProvider == x.dependencySourcesProvider) && (this.canReload == x.canReload)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.BuildServerCapabilities".##) + compileProvider.##) + testProvider.##) + runProvider.##) + dependencySourcesProvider.##) + canReload.##)
  }
  override def toString: String = {
    "BuildServerCapabilities(" + compileProvider + ", " + testProvider + ", " + runProvider + ", " + dependencySourcesProvider + ", " + canReload + ")"
  }
  private[this] def copy(compileProvider: Option[sbt.internal.bsp.CompileProvider] = compileProvider, testProvider: Option[sbt.internal.bsp.TestProvider] = testProvider, runProvider: Option[sbt.internal.bsp.RunProvider] = runProvider, dependencySourcesProvider: Option[Boolean] = dependencySourcesProvider, canReload: Option[Boolean] = canReload): BuildServerCapabilities = {
    new BuildServerCapabilities(compileProvider, testProvider, runProvider, dependencySourcesProvider, canReload)
  }
  def withCompileProvider(compileProvider: Option[sbt.internal.bsp.CompileProvider]): BuildServerCapabilities = {
    copy(compileProvider = compileProvider)
  }
  def withCompileProvider(compileProvider: sbt.internal.bsp.CompileProvider): BuildServerCapabilities = {
    copy(compileProvider = Option(compileProvider))
  }
  def withTestProvider(testProvider: Option[sbt.internal.bsp.TestProvider]): BuildServerCapabilities = {
    copy(testProvider = testProvider)
  }
  def withTestProvider(testProvider: sbt.internal.bsp.TestProvider): BuildServerCapabilities = {
    copy(testProvider = Option(testProvider))
  }
  def withRunProvider(runProvider: Option[sbt.internal.bsp.RunProvider]): BuildServerCapabilities = {
    copy(runProvider = runProvider)
  }
  def withRunProvider(runProvider: sbt.internal.bsp.RunProvider): BuildServerCapabilities = {
    copy(runProvider = Option(runProvider))
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
  
  def apply(compileProvider: Option[sbt.internal.bsp.CompileProvider], testProvider: Option[sbt.internal.bsp.TestProvider], runProvider: Option[sbt.internal.bsp.RunProvider], dependencySourcesProvider: Option[Boolean], canReload: Option[Boolean]): BuildServerCapabilities = new BuildServerCapabilities(compileProvider, testProvider, runProvider, dependencySourcesProvider, canReload)
  def apply(compileProvider: sbt.internal.bsp.CompileProvider, testProvider: sbt.internal.bsp.TestProvider, runProvider: sbt.internal.bsp.RunProvider, dependencySourcesProvider: Boolean, canReload: Boolean): BuildServerCapabilities = new BuildServerCapabilities(Option(compileProvider), Option(testProvider), Option(runProvider), Option(dependencySourcesProvider), Option(canReload))
}

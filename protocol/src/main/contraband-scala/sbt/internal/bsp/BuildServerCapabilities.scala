/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
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
  val debugProvider: Option[sbt.internal.bsp.DebugProvider],
  val dependencySourcesProvider: Option[Boolean],
  val resourcesProvider: Option[Boolean],
  val outputPathsProvider: Option[Boolean],
  val canReload: Option[Boolean],
  val jvmRunEnvironmentProvider: Option[Boolean],
  val jvmTestEnvironmentProvider: Option[Boolean]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: BuildServerCapabilities => (this.compileProvider == x.compileProvider) && (this.testProvider == x.testProvider) && (this.runProvider == x.runProvider) && (this.debugProvider == x.debugProvider) && (this.dependencySourcesProvider == x.dependencySourcesProvider) && (this.resourcesProvider == x.resourcesProvider) && (this.outputPathsProvider == x.outputPathsProvider) && (this.canReload == x.canReload) && (this.jvmRunEnvironmentProvider == x.jvmRunEnvironmentProvider) && (this.jvmTestEnvironmentProvider == x.jvmTestEnvironmentProvider)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.BuildServerCapabilities".##) + compileProvider.##) + testProvider.##) + runProvider.##) + debugProvider.##) + dependencySourcesProvider.##) + resourcesProvider.##) + outputPathsProvider.##) + canReload.##) + jvmRunEnvironmentProvider.##) + jvmTestEnvironmentProvider.##)
  }
  override def toString: String = {
    "BuildServerCapabilities(" + compileProvider + ", " + testProvider + ", " + runProvider + ", " + debugProvider + ", " + dependencySourcesProvider + ", " + resourcesProvider + ", " + outputPathsProvider + ", " + canReload + ", " + jvmRunEnvironmentProvider + ", " + jvmTestEnvironmentProvider + ")"
  }
  private def copy(compileProvider: Option[sbt.internal.bsp.CompileProvider] = compileProvider, testProvider: Option[sbt.internal.bsp.TestProvider] = testProvider, runProvider: Option[sbt.internal.bsp.RunProvider] = runProvider, debugProvider: Option[sbt.internal.bsp.DebugProvider] = debugProvider, dependencySourcesProvider: Option[Boolean] = dependencySourcesProvider, resourcesProvider: Option[Boolean] = resourcesProvider, outputPathsProvider: Option[Boolean] = outputPathsProvider, canReload: Option[Boolean] = canReload, jvmRunEnvironmentProvider: Option[Boolean] = jvmRunEnvironmentProvider, jvmTestEnvironmentProvider: Option[Boolean] = jvmTestEnvironmentProvider): BuildServerCapabilities = {
    new BuildServerCapabilities(compileProvider, testProvider, runProvider, debugProvider, dependencySourcesProvider, resourcesProvider, outputPathsProvider, canReload, jvmRunEnvironmentProvider, jvmTestEnvironmentProvider)
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
  def withDebugProvider(debugProvider: Option[sbt.internal.bsp.DebugProvider]): BuildServerCapabilities = {
    copy(debugProvider = debugProvider)
  }
  def withDebugProvider(debugProvider: sbt.internal.bsp.DebugProvider): BuildServerCapabilities = {
    copy(debugProvider = Option(debugProvider))
  }
  def withDependencySourcesProvider(dependencySourcesProvider: Option[Boolean]): BuildServerCapabilities = {
    copy(dependencySourcesProvider = dependencySourcesProvider)
  }
  def withDependencySourcesProvider(dependencySourcesProvider: Boolean): BuildServerCapabilities = {
    copy(dependencySourcesProvider = Option(dependencySourcesProvider))
  }
  def withResourcesProvider(resourcesProvider: Option[Boolean]): BuildServerCapabilities = {
    copy(resourcesProvider = resourcesProvider)
  }
  def withResourcesProvider(resourcesProvider: Boolean): BuildServerCapabilities = {
    copy(resourcesProvider = Option(resourcesProvider))
  }
  def withOutputPathsProvider(outputPathsProvider: Option[Boolean]): BuildServerCapabilities = {
    copy(outputPathsProvider = outputPathsProvider)
  }
  def withOutputPathsProvider(outputPathsProvider: Boolean): BuildServerCapabilities = {
    copy(outputPathsProvider = Option(outputPathsProvider))
  }
  def withCanReload(canReload: Option[Boolean]): BuildServerCapabilities = {
    copy(canReload = canReload)
  }
  def withCanReload(canReload: Boolean): BuildServerCapabilities = {
    copy(canReload = Option(canReload))
  }
  def withJvmRunEnvironmentProvider(jvmRunEnvironmentProvider: Option[Boolean]): BuildServerCapabilities = {
    copy(jvmRunEnvironmentProvider = jvmRunEnvironmentProvider)
  }
  def withJvmRunEnvironmentProvider(jvmRunEnvironmentProvider: Boolean): BuildServerCapabilities = {
    copy(jvmRunEnvironmentProvider = Option(jvmRunEnvironmentProvider))
  }
  def withJvmTestEnvironmentProvider(jvmTestEnvironmentProvider: Option[Boolean]): BuildServerCapabilities = {
    copy(jvmTestEnvironmentProvider = jvmTestEnvironmentProvider)
  }
  def withJvmTestEnvironmentProvider(jvmTestEnvironmentProvider: Boolean): BuildServerCapabilities = {
    copy(jvmTestEnvironmentProvider = Option(jvmTestEnvironmentProvider))
  }
}
object BuildServerCapabilities {
  
  def apply(compileProvider: Option[sbt.internal.bsp.CompileProvider], testProvider: Option[sbt.internal.bsp.TestProvider], runProvider: Option[sbt.internal.bsp.RunProvider], debugProvider: Option[sbt.internal.bsp.DebugProvider], dependencySourcesProvider: Option[Boolean], resourcesProvider: Option[Boolean], outputPathsProvider: Option[Boolean], canReload: Option[Boolean], jvmRunEnvironmentProvider: Option[Boolean], jvmTestEnvironmentProvider: Option[Boolean]): BuildServerCapabilities = new BuildServerCapabilities(compileProvider, testProvider, runProvider, debugProvider, dependencySourcesProvider, resourcesProvider, outputPathsProvider, canReload, jvmRunEnvironmentProvider, jvmTestEnvironmentProvider)
  def apply(compileProvider: sbt.internal.bsp.CompileProvider, testProvider: sbt.internal.bsp.TestProvider, runProvider: sbt.internal.bsp.RunProvider, debugProvider: sbt.internal.bsp.DebugProvider, dependencySourcesProvider: Boolean, resourcesProvider: Boolean, outputPathsProvider: Boolean, canReload: Boolean, jvmRunEnvironmentProvider: Boolean, jvmTestEnvironmentProvider: Boolean): BuildServerCapabilities = new BuildServerCapabilities(Option(compileProvider), Option(testProvider), Option(runProvider), Option(debugProvider), Option(dependencySourcesProvider), Option(resourcesProvider), Option(outputPathsProvider), Option(canReload), Option(jvmRunEnvironmentProvider), Option(jvmTestEnvironmentProvider))
}

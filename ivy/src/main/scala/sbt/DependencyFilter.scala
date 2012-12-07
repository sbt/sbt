/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt

trait DependencyFilterExtra
{
	def moduleFilter(organization: NameFilter = AllPassFilter, name: NameFilter = AllPassFilter, revision: NameFilter = AllPassFilter): ModuleFilter =
		new ModuleFilter {
			def apply(m: ModuleID): Boolean = organization.accept(m.organization) && name.accept(m.name) && revision.accept(m.revision)
		}
	def artifactFilter(name: NameFilter = AllPassFilter, `type`: NameFilter = AllPassFilter, extension: NameFilter = AllPassFilter, classifier: NameFilter = AllPassFilter): ArtifactFilter =
		new ArtifactFilter {
			def apply(a: Artifact): Boolean = name.accept(a.name) && `type`.accept(a.`type`) && extension.accept(a.extension) && classifier.accept(a.classifier getOrElse "")
		}
	def configurationFilter(name: NameFilter = AllPassFilter): ConfigurationFilter =
		new ConfigurationFilter {
			def apply(c: String): Boolean = name.accept(c)
		}
}
object DependencyFilter extends DependencyFilterExtra
{
	def make(configuration: ConfigurationFilter = configurationFilter(), module: ModuleFilter = moduleFilter(), artifact: ArtifactFilter = artifactFilter()): DependencyFilter =
		new DependencyFilter {
			def apply(c: String, m: ModuleID, a: Artifact): Boolean = configuration(c) && module(m) && artifact(a)
		}
	def apply(x: DependencyFilter, y: DependencyFilter, combine: (Boolean, Boolean) => Boolean): DependencyFilter =
		new DependencyFilter {
			def apply(c: String, m: ModuleID, a: Artifact): Boolean = combine(x(c, m, a), y(c, m, a))
		}
	def allPass: DependencyFilter = configurationFilter()
	implicit def fnToModuleFilter(f: ModuleID => Boolean): ModuleFilter = new ModuleFilter { def apply(m: ModuleID) = f(m) }
	implicit def fnToArtifactFilter(f: Artifact => Boolean): ArtifactFilter = new ArtifactFilter { def apply(m: Artifact) = f(m) }
	implicit def fnToConfigurationFilter(f: String => Boolean): ConfigurationFilter = new ConfigurationFilter { def apply(c: String) = f(c) }
	implicit def subDepFilterToFn[Arg](f: SubDepFilter[Arg, _]): Arg => Boolean = f apply _
}
trait DependencyFilter
{
	def apply(configuration: String, module: ModuleID, artifact: Artifact): Boolean
	final def &&(o: DependencyFilter) = DependencyFilter(this, o, _ && _)
	final def ||(o: DependencyFilter) = DependencyFilter(this, o, _ || _)
	final def -- (o: DependencyFilter) = DependencyFilter(this, o, _ && !_)
}
sealed trait SubDepFilter[Arg, Self <: SubDepFilter[Arg, Self]] extends DependencyFilter
{ self: Self =>
	def apply(a: Arg): Boolean
	protected def make(f: Arg => Boolean): Self
	final def &(o: Self): Self = combine(o, _ && _)
	final def |(o: Self): Self = combine(o, _ || _)
	final def -(o: Self): Self = combine(o, _ && !_)
	private[this] def combine(o: Self, f: (Boolean, Boolean) => Boolean): Self = make( (m: Arg) => f(this(m), o(m)) )
}
trait ModuleFilter extends SubDepFilter[ModuleID, ModuleFilter]
{
	protected final def make(f: ModuleID => Boolean) = new ModuleFilter { def apply(m: ModuleID) = f(m) }
	final def apply(configuration: String, module: ModuleID, artifact: Artifact): Boolean = apply(module)
}
trait ArtifactFilter extends SubDepFilter[Artifact, ArtifactFilter]
{
	protected final def make(f: Artifact => Boolean) = new ArtifactFilter { def apply(m: Artifact) = f(m) }
	final def apply(configuration: String, module: ModuleID, artifact: Artifact): Boolean = apply(artifact)
}
trait ConfigurationFilter extends SubDepFilter[String, ConfigurationFilter]
{
	protected final def make(f: String => Boolean) = new ConfigurationFilter { def apply(m: String) = f(m) }
	final def apply(configuration: String, module: ModuleID, artifact: Artifact): Boolean = apply(configuration)
}
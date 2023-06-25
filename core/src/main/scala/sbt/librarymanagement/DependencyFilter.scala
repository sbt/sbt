/* sbt -- Simple Build Tool
 * Copyright 2011  Mark Harrah
 */
package sbt.librarymanagement

import sbt.io.{ AllPassFilter, NameFilter }

trait DependencyFilterExtra {
  // See http://www.scala-lang.org/news/2.12.0#traits-compile-to-interfaces
  // Avoid defining fields (val or var, but a constant is ok – final val without result type)
  // Avoid calling super
  // Avoid initializer statements in the body

  def moduleFilter(
      organization: NameFilter = AllPassFilter,
      name: NameFilter = AllPassFilter,
      revision: NameFilter = AllPassFilter
  ): ModuleFilter =
    new ModuleFilter {
      def apply(m: ModuleID): Boolean =
        organization.accept(m.organization) && name.accept(m.name) && revision.accept(m.revision)
    }

  def artifactFilter(
      name: NameFilter = AllPassFilter,
      `type`: NameFilter = AllPassFilter,
      extension: NameFilter = AllPassFilter,
      classifier: NameFilter = AllPassFilter
  ): ArtifactFilter =
    new ArtifactFilter {
      def apply(a: Artifact): Boolean =
        name.accept(a.name) && `type`.accept(a.`type`) && extension.accept(
          a.extension
        ) && classifier
          .accept(a.classifier getOrElse "")
    }

  def configurationFilter(name: NameFilter = AllPassFilter): ConfigurationFilter =
    new ConfigurationFilter {
      def apply(c: ConfigRef): Boolean = name.accept(c.name)
    }
}

object DependencyFilter extends DependencyFilterExtra {
  def make(
      configuration: ConfigurationFilter = configurationFilter(),
      module: ModuleFilter = moduleFilter(),
      artifact: ArtifactFilter = artifactFilter()
  ): DependencyFilter =
    new DependencyFilter {
      def apply(c: ConfigRef, m: ModuleID, a: Artifact): Boolean =
        configuration(c) && module(m) && artifact(a)
    }
  def apply(
      x: DependencyFilter,
      y: DependencyFilter,
      combine: (Boolean, Boolean) => Boolean
  ): DependencyFilter =
    new DependencyFilter {
      def apply(c: ConfigRef, m: ModuleID, a: Artifact): Boolean = combine(x(c, m, a), y(c, m, a))
    }
  def allPass: DependencyFilter = configurationFilter()
  implicit def fnToModuleFilter(f: ModuleID => Boolean): ModuleFilter = new ModuleFilter {
    def apply(m: ModuleID) = f(m)
  }
  implicit def fnToArtifactFilter(f: Artifact => Boolean): ArtifactFilter = new ArtifactFilter {
    def apply(m: Artifact) = f(m)
  }
  implicit def fnToConfigurationFilter(f: ConfigRef => Boolean): ConfigurationFilter =
    new ConfigurationFilter { def apply(c: ConfigRef) = f(c) }
  implicit def subDepFilterToFn[Arg](f: SubDepFilter[Arg, _]): Arg => Boolean = f apply _
}
trait DependencyFilter {
  def apply(configuration: ConfigRef, module: ModuleID, artifact: Artifact): Boolean
  final def &&(o: DependencyFilter) = DependencyFilter(this, o, _ && _)
  final def ||(o: DependencyFilter) = DependencyFilter(this, o, _ || _)
  final def --(o: DependencyFilter) = DependencyFilter(this, o, _ && !_)
}
sealed trait SubDepFilter[Arg, Self <: SubDepFilter[Arg, Self]] extends DependencyFilter {
  self: Self =>
  def apply(a: Arg): Boolean
  protected def make(f: Arg => Boolean): Self
  final def &(o: Self): Self = combine(o, _ && _)
  final def |(o: Self): Self = combine(o, _ || _)
  final def -(o: Self): Self = combine(o, _ && !_)
  private[this] def combine(o: Self, f: (Boolean, Boolean) => Boolean): Self =
    make((m: Arg) => f(this(m), o(m)))
}
trait ModuleFilter extends SubDepFilter[ModuleID, ModuleFilter] {
  protected final def make(f: ModuleID => Boolean) = new ModuleFilter {
    def apply(m: ModuleID) = f(m)
  }
  final def apply(configuration: ConfigRef, module: ModuleID, artifact: Artifact): Boolean =
    apply(module)
}
trait ArtifactFilter extends SubDepFilter[Artifact, ArtifactFilter] {
  protected final def make(f: Artifact => Boolean) = new ArtifactFilter {
    def apply(m: Artifact) = f(m)
  }
  final def apply(configuration: ConfigRef, module: ModuleID, artifact: Artifact): Boolean =
    apply(artifact)
}
trait ConfigurationFilter extends SubDepFilter[ConfigRef, ConfigurationFilter] {
  protected final def make(f: ConfigRef => Boolean) = new ConfigurationFilter {
    def apply(m: ConfigRef) = f(m)
  }
  final def apply(configuration: ConfigRef, module: ModuleID, artifact: Artifact): Boolean =
    apply(configuration)
}

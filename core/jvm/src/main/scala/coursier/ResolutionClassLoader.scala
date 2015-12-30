package coursier

import java.io.File
import java.net.URLClassLoader

import coursier.util.ClasspathFilter

class ResolutionClassLoader(
  val resolution: Resolution,
  val artifacts: Seq[(Dependency, Artifact, File)],
  parent: ClassLoader
) extends URLClassLoader(
  artifacts.map { case (_, _, f) => f.toURI.toURL }.toArray,
  parent
) {

  /**
   * Filtered version of this `ClassLoader`, exposing only `dependencies` and their
   * their transitive dependencies, and filtering out the other dependencies from
   * `resolution` - for `ClassLoader` isolation.
   *
   * An application launched by `coursier launch -C` has `ResolutionClassLoader` set as its
   * context `ClassLoader` (can be obtain with `Thread.currentThread().getContextClassLoader`).
   * If it aims at doing `ClassLoader` isolation, exposing only a dependency `dep` to the isolated
   * things, `filter(dep)` provides a `ClassLoader` that loaded `dep` and all its transitive
   * dependencies through the same loader as the contextual one, but that "exposes" only
   * `dep` and its transitive dependencies, nothing more.
   */
  def filter(dependencies: Set[Dependency]): ClassLoader = {
    val subRes = resolution.subset(dependencies)
    val subArtifacts = subRes.dependencyArtifacts.map { case (_, a) => a }.toSet
    val subFiles = artifacts.collect { case (_, a, f) if subArtifacts(a) => f }

    new ClasspathFilter(this, subFiles.toSet, exclude = false)
  }

}

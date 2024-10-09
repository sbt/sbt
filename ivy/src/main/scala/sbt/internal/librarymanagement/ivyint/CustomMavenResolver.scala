package sbt.internal.librarymanagement
package ivyint

import org.apache.ivy.plugins.resolver.DependencyResolver
import sbt.librarymanagement._

// These are placeholder traits for sbt-aether-resolver
trait CustomMavenResolver extends DependencyResolver {}
trait CustomRemoteMavenResolver extends CustomMavenResolver {
  def repo: MavenRepository
}

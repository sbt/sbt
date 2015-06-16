package coursier

package object test {

  implicit class DependencyOps(val underlying: Dependency) extends AnyVal {
    def withCompileScope: Dependency = underlying.copy(scope = Scope.Compile)
  }

}

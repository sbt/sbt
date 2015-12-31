package sbt

private[sbt] object CompatibilityWarning {
  def apply(module: IvySbt#Module, mavenStyle: Boolean, log: Logger): Unit = {
    if (mavenStyle) {
      processIntransitive(module, log)
    }
  }

  def processIntransitive(module: IvySbt#Module, log: Logger): Unit = {
    val directDependencies: Seq[ModuleID] = module.moduleSettings match {
      case x: InlineConfiguration             => x.dependencies
      case x: InlineConfigurationWithExcludes => x.dependencies
      case _                                  => Seq()
    }
    directDependencies foreach { m =>
      if (!m.isTransitive) {
        log.warn(
          s"""Found intransitive dependency ($m), but Maven repositories do not support intransitive dependencies.
             |  Use exclusions instead so transitive dependencies will be correctly excluded in dependent projects.
           """.stripMargin)
      } else ()
    }
  }
}

import Configurations.{CompilerPlugin => CPlugin}

lazy val use = project.dependsOn(file("def") % CPlugin).settings(
	autoCompilerPlugins := true
)

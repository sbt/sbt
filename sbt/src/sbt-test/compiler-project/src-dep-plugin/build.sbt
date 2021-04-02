import Configurations.{CompilerPlugin => CPlugin}

lazy val use = project.dependsOn(RootProject(file("def")) % CPlugin).settings(
	autoCompilerPlugins := true
)

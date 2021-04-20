// TODO - currently root plugins (trigger == allRequirements && requires = Nil) can't be disabled, ever.
//lazy val noCore = project.disablePlugins(plugins.CorePlugin)

lazy val noIvy = project.disablePlugins(plugins.IvyPlugin)

lazy val noJvm = project.disablePlugins(plugins.JvmPlugin)

lazy val noJunit = project.disablePlugins(plugins.JUnitXmlReportPlugin)

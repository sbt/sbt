package demo

	import scala.tools.nsc.{Global, plugins}

class DemoPlugin(val global: Global) extends plugins.Plugin
{
	val name = "demo-plugin"
	val description = "Throws an error"
	val components = sys.error("The plugin was successfully registered.")
}

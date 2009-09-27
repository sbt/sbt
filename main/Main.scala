package xsbt

class Main extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
	{
		println(configuration.arguments.mkString("\n"))
		Exit(0)
	}
}

final case class Exit(code: Int) extends xsbti.Exit
package xsbt

import xsbti.{Exit => IExit, MainResult, SbtConfiguration, SbtMain}

class Main extends xsbti.SbtMain
{
	def run(configuration: SbtConfiguration): MainResult =
	{
		println(configuration.arguments.mkString("\n"))
		Exit(0)
	}
}

final case class Exit(code: Int) extends IExit
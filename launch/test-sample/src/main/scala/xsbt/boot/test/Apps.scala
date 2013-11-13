/** These are packaged and published locally and the resulting artifact is used to test the launcher.*/
package xsbt.boot.test

class Exit(val code: Int) extends xsbti.Exit
final class MainException(message: String) extends RuntimeException(message)
final class ArgumentTest extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration) =
		if(configuration.arguments.length == 0)
			throw new MainException("Arguments were empty")
		else
			new Exit(0)
}
class AppVersionTest extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration) =
	{
		val expected = configuration.arguments.headOption.getOrElse("")
		if(configuration.provider.id.version == expected)
			new Exit(0)
		else
			throw new MainException("app version was " + configuration.provider.id.version + ", expected: " + expected)
	}
}
class ExtraTest extends xsbti.AppMain
{
	def run(configuration: xsbti.AppConfiguration) =
	{
		configuration.arguments.foreach { arg =>
			if(getClass.getClassLoader.getResource(arg) eq null)
				throw new MainException("Could not find '" + arg + "'")
		}
		new Exit(0)
	}
}
object PlainArgumentTestWithReturn {
  def main(args: Array[String]): Int =
    if(args.length == 0) 1
    else 0
}
object PlainArgumentTest {
  def main(args: Array[String]): Unit =
    if(args.length == 0) throw new MainException("Arguments were empty")
    else ()
}
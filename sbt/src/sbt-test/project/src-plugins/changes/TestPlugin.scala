import sbt._

trait TestPlugin extends DefaultProject
{
	lazy val check =
		task
		{
			try
			{
				Class.forName("JavaTest")
				Some("Java source should not be compiled as part of the plugin")
			}
			catch
			{
				case _: ClassNotFoundException =>
					log.info("Test action")
					None
			}
		}
}
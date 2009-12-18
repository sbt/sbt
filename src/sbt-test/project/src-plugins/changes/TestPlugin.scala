import sbt._

trait TestPlugin extends DefaultProject
{
	lazy val check = task { log.info("Test action"); None }
}
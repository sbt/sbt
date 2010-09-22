import sbt._

trait Marker extends NotNull
{ self: Project =>
	def toMark: Path = "ran"
	def mark() =
	{
		if(toMark.exists)
			Some("Already ran")
		else
			FileUtilities.touch(toMark, log)
	}
}
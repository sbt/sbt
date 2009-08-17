package xsbt

import org.scalacheck.Prop._

object checkResult
{
	def apply[T](run: => T, expected: T) =
	{
		("Expected: " + expected) |:
		(try
		{
			val actual = run
			("Actual: " + actual) |: (actual == expected)
		}
		catch
		{
			case TasksFailed(failures) =>
				failures.foreach(f => f.exception.printStackTrace)
				"One or more tasks failed" |: false
			case e =>
				e.printStackTrace
				"Error in framework" |: false
		})
	}
}
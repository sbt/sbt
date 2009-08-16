package xsbt

import org.scalacheck.Prop._

object checkResult
{
	def apply[T](run: => Either[List[WorkFailure[Task[_]]],T], expected: T) =
	{
		("Expected: " + expected) |:
		(try
		{
			val actual = run
			("Actual: " + actual) |:
			(actual match
			{
				case Right(a) => a == expected
				case Left(failures) =>
					failures.foreach(f => f.exception.printStackTrace)
					false
			})
		}
		catch
		{
			case e =>
				e.printStackTrace
				"Error in framework" |: false
		})
	}
}
package sbt.impl

private[sbt] object SelectMainClass
{
	def apply(promptIfMultipleChoices: Boolean, mainClasses: List[String]) =
	{
		mainClasses match
		{
			case Nil => None
			case head :: Nil => Some(head)
			case multiple =>
				if(promptIfMultipleChoices)
				{
					println("\nMultiple main classes detected, select one to run:\n")
					for( (className, index) <- multiple.zipWithIndex )
						println(" [" + (index+1) + "] " + className)
					val line = trim(SimpleReader.readLine("\nEnter number: "))
					println("")
					toInt(line, multiple.length) map multiple.apply
				}
				else
					None
		}
	}
	private def trim(s: Option[String]) = s.getOrElse("")
	private def toInt(s: String, size: Int) =
		try
		{
			val i = s.toInt
			if(i > 0 && i <= size)
				Some(i-1)
			else
			{
				println("Number out of range: was " + i + ", expected number between 1 and " + size)
				None
			}
		}
		catch
		{
			case nfe: NumberFormatException =>
				println("Invalid number: " + nfe.toString)
				None
		}
}
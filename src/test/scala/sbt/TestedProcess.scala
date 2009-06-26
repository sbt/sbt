package sbt

import java.io.File

object exit
{
	def main(args: Array[String])
	{
		System.exit(java.lang.Integer.parseInt(args(0)))
	}
}
object cat
{
	def main(args: Array[String])
	{
		val result =
			if(args.length == 0)
				FileUtilities.transfer(System.in, System.out, log)
			else
				catFiles(args.toList)
		result match
		{
			case Some(err) => System.err.println("Error: " + err); System.exit(1)
			case None => System.exit(0)
		}
	}
	private val log = new ConsoleLogger
	private def catFiles(filenames: List[String]): Option[String] =
	{
		filenames match
		{
			case head :: tail =>
				val file = new File(head)
				if(file.isDirectory)
					Some("Is directory: " + file)
				else if(file.exists)
				{
					FileUtilities.readStream(file, log) { stream =>
						FileUtilities.transfer(stream, System.out, log)
					}
					catFiles(tail)
				}
				else
					Some("No such file or directory: " + file)
			case Nil => None
		}
	}
}
object echo
{
	def main(args: Array[String])
	{
		System.out.println(args.mkString(" "))
	}
}
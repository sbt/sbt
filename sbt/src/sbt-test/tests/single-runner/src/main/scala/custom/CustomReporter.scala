package custom

import java.io._
import org.scalatest._
import events._

class CustomReporter extends Reporter {

	private def writeFile(filePath: String, content: String): Unit = {
		val file = new File(filePath)
		val writer =
			if (!file.exists)
				new FileWriter(new File(filePath))
			else
				new FileWriter(new File(filePath + "-2"))
		writer.write(content)
		writer.flush()
		writer.close()
	}

	def apply(event: Event): Unit = {
		event match {
			case runStarting: RunStarting => writeFile("target/RunStarting", "RunStarting")
			case _ =>
		}
	}
}

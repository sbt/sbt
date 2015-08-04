package custom

import java.io._
import org.scalatest._
import events._

class CustomReporter extends ResourcefulReporter {

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
			case runCompleted: RunCompleted => writeFile("target/RunCompleted", "RunCompleted")
			case _ =>
		}
	}

	def dispose(): Unit = {
		val file = new File("target/dispose")
		val filePath =
			if (file.exists)
				"target/dispose2"
			else
				"target/dispose"
		writeFile(filePath, "dispose")
	}
}

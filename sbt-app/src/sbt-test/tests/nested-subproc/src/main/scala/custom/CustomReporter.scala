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
			case SuiteStarting(_, suiteName, _, _, _, _, _, _, _, _) => writeFile("target/SuiteStarting-" + suiteName, suiteName)
			case SuiteCompleted(_, suiteName, _, _, _, _, _, _, _, _, _) => writeFile("target/SuiteCompleted-" + suiteName, suiteName)
			case TestStarting(_, _, _, _, testName, _, _, _, _, _, _, _) => writeFile("target/TestStarting-" + testName, testName)
			case TestSucceeded(_, _, _, _, testName, _, _, _, _, _, _, _, _, _) => writeFile("target/TestSucceeded-" + testName, testName)
			case _ =>
		}
	}
}

import sbt._
import Keys._
import Tests._
import Defaults._

object ForkTestsTest extends Build {
	val totalFiles = 9
	val groupSize = 3

	val check = TaskKey[Unit]("check", "Check all files were created and remove them.")

	def groupId(idx: Int) = "group_" + (idx + 1)
	def groupPrefix(idx: Int) = groupId(idx) + "_file_"

	lazy val root = Project("root", file("."), settings = defaultSettings ++  Seq(
		testGrouping <<= definedTests in Test map { tests =>
			assert(tests.size == 1)
			val groups = Stream const tests(0) take totalFiles grouped groupSize
			for ((ts, idx) <- groups.toSeq.zipWithIndex)	yield {
				new Group(groupId(idx), ts, SubProcess(Seq("-Dgroup.prefix=" + groupPrefix(idx), "-Dgroup.size=" + ts.size)))
			}
		},
		check := {
			for (i <- 0 until totalFiles/groupSize)
				for (j <- 1 to groupSize) {
					val f = file(groupPrefix(i) + j)
					if (!f.exists)
						error("File " + f.getName + " was not created.")
					else
						f.delete()
				}
		},
		concurrentRestrictions := Tags.limit(Tags.TestGroup, 2) :: Nil,
		libraryDependencies += "org.scalatest" % "scalatest_2.9.0" % "1.6.1" % "test"
	))
}

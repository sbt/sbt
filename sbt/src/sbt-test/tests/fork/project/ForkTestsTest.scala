import sbt._
import Keys._
import Tests._
import Defaults._

object ForkTestsTest extends Build {
	val groupSize = 3
	val groups = 3

	val check = TaskKey[Unit]("check", "Check all files were created and remove them.")

	def groupId(idx: Int) = "group_" + (idx + 1)
	def groupPrefix(idx: Int) = groupId(idx) + "_file_"

	lazy val root = Project("root", file("."), settings = defaultSettings ++  Seq(
		scalaVersion := "2.9.2",
		testGrouping in Test <<= definedTests in Test map { tests =>
			assert(tests.size == 3)
			for (idx <- 0 until groups) yield
				new Group(groupId(idx), tests, SubProcess(Seq("-Dgroup.prefix=" + groupPrefix(idx))))
		},
		check := {
			val files =
				for(i <- 0 until groups; j <- 1 to groupSize) yield
					file(groupPrefix(i) + j)
			val (exist, absent) = files.partition(_.exists)
			exist.foreach(_.delete())
			if(absent.nonEmpty)
				sys.error("Files were not created:\n\t" + absent.mkString("\n\t"))
		},
		concurrentRestrictions := Tags.limit(Tags.ForkedTestGroup, 2) :: Nil,
		libraryDependencies += "org.scalatest" %% "scalatest" % "1.8" % "test"
	))
}

lazy val taskX = taskKey[Set[Int]]("numbers")
lazy val filterX = ScopeFilter( inDependencies(ThisProject, transitive=false, includeRoot=false) )

lazy val filterA: ScopeFilter.ScopeFilter = ScopeFilter(
	inAggregates( LocalProject(e.id) ),
	inConfigurations(Compile,Test) || inZeroConfiguration,
	inTasks(console) || inZeroTask
)

lazy val eGlobal = Set(192, 210)
lazy val cGlobal = Set(123, 57)
lazy val cCompile = Set(694)
lazy val cTest = Set(98)
lazy val dGlobal = Set(43)
lazy val dConsole = Set(102)
lazy val yDefault = Set(-1)
lazy val aDefault = Set(-31)

lazy val aExpected = aDefault ++ eGlobal ++ cGlobal ++ cCompile ++ cGlobal ++ cTest ++
	dGlobal ++ dConsole ++ Set(Compile.name.length, Test.name.length)
lazy val xExpected = aExpected ++ yDefault
lazy val check = taskKey[Unit]("verifies tasks generate the right values")
def checkImpl(actual: Set[Int], expected: Set[Int]): Unit =
	assert(actual == expected, s"Expected: $expected \nActual: $actual")


lazy val x = project.dependsOn(y).settings(
	taskX := taskX.all(filterX).value.flatten.toSet,
	check := checkImpl(taskX.value, xExpected)
)

lazy val y = project.dependsOn(z).settings(
	taskX := (taskX ?? yDefault).all( ScopeFilter( inProjects(a, z) ) ).value.toSet.flatten
)

lazy val z = project


lazy val aTaskX = Def.task {
	val tx: Option[Set[Int]] = taskX.?.value
	val conf = configuration.?.value
	(tx, conf) match {
		case (Some(v), _) => v
		case (None, Some(c)) => Set(c.name.length)
		case (None, None) => aDefault
	}
}

lazy val a = project.settings(
	taskX := aTaskX.all(filterA).value.flatten.toSet,
	check := checkImpl(taskX.value, aExpected)
)

lazy val e = project.aggregate(b,c,d).settings(
	taskX := eGlobal
)

lazy val b = project

lazy val c = project.settings(
	taskX := cGlobal,
	taskX in Compile := cCompile,
	taskX in Test := cTest
)

lazy val d = project.settings(
	taskX := dGlobal,
	taskX in (Compile,console) := dConsole,
	// these shouldn't get picked up
	taskX in (Compile,compile) := Set(32366),
	taskX in compile := Set(548686),
	taskX in Configurations.IntegrationTest := Set(11111)
)
// this test is specific to the old incremental compilation algorithm
incOptions := incOptions.value.withNameHashing(false)

lazy val verifyDeps = taskKey[Unit]("verify inherited dependencies are properly extracted")

verifyDeps := {
	val a = compile.in(Compile).value
	val baseDir = baseDirectory.value
	def relative(f: java.io.File): java.io.File =  f.relativeTo(baseDir) getOrElse f
	def toFile(s: String) = relative(baseDir / (s + ".scala"))
	def inheritedDeps(name: String): Set[File] = {
		val file = (baseDir / (name + ".scala")).getAbsoluteFile
		val absoluteFiles = a.relations.publicInherited.internal.forward(file)
		absoluteFiles.map(relative)
	}
	val ADeps = Set("C", "D", "E", "G", "J").map(toFile)
	same(inheritedDeps("A"), ADeps)
	val BDeps = Set.empty[File]
	same(inheritedDeps("B"), BDeps)
	val CDeps = Set("D", "G", "J").map(toFile)
	same(inheritedDeps("C"), CDeps)
	val DDeps = Set("G", "J").map(toFile)
	same(inheritedDeps("D"), DDeps)
	val EDeps = Set.empty[File]
	same(inheritedDeps("E"), EDeps)
	val FDeps = Set("C", "D", "G", "J").map(toFile)
	same(inheritedDeps("F"), FDeps)
	val GDeps = Set("J").map(toFile)
	same(inheritedDeps("G"), GDeps)
	val JDeps = Set.empty[File]
	same(inheritedDeps("J"), JDeps)
}

def same[T](x: T, y: T): Unit = {
	assert(x == y, s"\nActual: $x, \nExpected: $y")
}

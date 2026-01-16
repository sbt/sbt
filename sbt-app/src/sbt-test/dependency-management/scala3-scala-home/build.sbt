scalaVersion := "3.3.4"

val makeHome = taskKey[Unit]("Populates the 'home/lib' directory with Scala jars from the default ScalaInstance")

makeHome := Def.uncached {
	val lib = baseDirectory.value / "home" / "lib"
	for(jar <- scalaInstance.value.allJars)
		IO.copyFile(jar, lib / jar.getName)
}

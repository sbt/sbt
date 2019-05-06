import sbt.nio.file.Glob

cleanKeepFiles ++= Seq(
	target.value / "keep",
	target.value / "keepfile"
)

cleanKeepGlobs += target.value.toGlob / "keepdir" / **
// This is necessary because recursive globs do not include the base directory.
cleanKeepGlobs += Glob(target.value / "keepdir")

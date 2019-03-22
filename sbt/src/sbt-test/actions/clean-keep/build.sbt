cleanKeepFiles ++= Seq(
	target.value / "keep",
	target.value / "keepfile"
)

cleanKeepGlobs += target.value / "keepdir" ** AllPassFilter

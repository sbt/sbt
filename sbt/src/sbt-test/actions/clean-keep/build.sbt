cleanFiles := Seq(target.value)

cleanKeepFiles ++= Seq(
  target.value / "keep",
  target.value / "keepfile",
  target.value / "keepdir"
)

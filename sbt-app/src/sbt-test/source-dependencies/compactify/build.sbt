TaskKey[Unit]("outputEmpty") := {
  val c = fileConverter.value
  val dir = (Compile / products).value.head
  def classes = dir.**("*.class").get()
  if (!classes.isEmpty) sys.error("Classes existed:\n\t" + classes.mkString("\n\t"))
}

// apparently Travis CI stopped allowing long file names
// it fails with the default setting of 255 characters so
// we have to set lower limit ourselves
scalacOptions ++= Seq("-Xmax-classfile-name", "240")

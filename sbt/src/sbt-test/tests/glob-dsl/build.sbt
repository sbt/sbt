// The project contains two files: { Foo.txt, Bar.md } in the subdirector base/subdir/nested-subdir

// Check that we can correctly extract Foo.txt with a recursive source
val foo = taskKey[Seq[File]]("Retrieve Foo.txt")

foo / fileInputs += baseDirectory.value ** "*.txt"

foo := (foo / fileInputs).value.all.map(_._1.toFile)

val checkFoo = taskKey[Unit]("Check that the Foo.txt file is retrieved")

checkFoo := assert(foo.value == Seq(baseDirectory.value / "base/subdir/nested-subdir/Foo.txt"))

// Check that we can correctly extract Bar.md with a non-recursive source
val bar = taskKey[Seq[File]]("Retrieve Bar.md")

bar / fileInputs += baseDirectory.value / "base/subdir/nested-subdir" * "*.md"

bar := (bar / fileInputs).value.all.map(_._1.toFile)

val checkBar = taskKey[Unit]("Check that the Bar.md file is retrieved")

checkBar := assert(bar.value == Seq(baseDirectory.value / "base/subdir/nested-subdir/Bar.md"))

// Check that we can correctly extract Bar.md and Foo.md with a non-recursive source
val all = taskKey[Seq[File]]("Retrieve all files")

all / fileInputs += baseDirectory.value / "base" / "subdir" / "nested-subdir" * AllPassFilter

val checkAll = taskKey[Unit]("Check that the Bar.md file is retrieved")

checkAll := {
  import sbt.dsl.LinterLevel.Ignore
  val expected = Set("Foo.txt", "Bar.md").map(baseDirectory.value / "base/subdir/nested-subdir" / _)
  assert((all / fileInputs).value.all.map(_._1.toFile).toSet == expected)
}

val set = taskKey[Seq[File]]("Specify redundant sources in a set")

set / fileInputs ++= Seq(
  baseDirectory.value / "base" ** -DirectoryFilter,
  baseDirectory.value / "base" / "subdir" / "nested-subdir" * -DirectoryFilter
)

val checkSet = taskKey[Unit]("Verify that redundant sources are handled")

checkSet := {
  val redundant = (set / fileInputs).value.all.map(_._1.toFile)
  assert(redundant.size == 4) // It should get Foo.txt and Bar.md twice

  val deduped = (set / fileInputs).value.toSet[Glob].all.map(_._1.toFile)
  val expected = Seq("Bar.md", "Foo.txt").map(baseDirectory.value / "base/subdir/nested-subdir" / _)
  assert(deduped.sorted == expected)

  val altDeduped = (set / fileInputs).value.unique.map(_._1.toFile)
  assert(altDeduped.sorted == expected)
}

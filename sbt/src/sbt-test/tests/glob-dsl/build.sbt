// The project contains two files: { Foo.txt, Bar.md } in the subdirector base/subdir/nested-subdir

// Check that we can correctly extract Foo.txt with a recursive source
val foo = taskKey[Seq[File]]("Retrieve Foo.txt")

foo / inputs += baseDirectory.value ** "*.txt"

foo := (foo / inputs).value.all

val checkFoo = taskKey[Unit]("Check that the Foo.txt file is retrieved")

checkFoo := assert(foo.value == Seq(baseDirectory.value / "base/subdir/nested-subdir/Foo.txt"))

// Check that we can correctly extract Bar.md with a non-recursive source
val bar = taskKey[Seq[File]]("Retrieve Bar.md")

bar / inputs += baseDirectory.value / "base/subdir/nested-subdir" * "*.md"

bar := (bar / inputs).value.all

val checkBar = taskKey[Unit]("Check that the Bar.md file is retrieved")

checkBar := assert(bar.value == Seq(baseDirectory.value / "base/subdir/nested-subdir/Bar.md"))

// Check that we can correctly extract Bar.md and Foo.md with a non-recursive source
val all = taskKey[Seq[File]]("Retrieve all files")

all / inputs += baseDirectory.value / "base" / "subdir" / "nested-subdir" * AllPassFilter

val checkAll = taskKey[Unit]("Check that the Bar.md file is retrieved")

checkAll := {
  import sbt.dsl.LinterLevel.Ignore
  val expected = Set("Foo.txt", "Bar.md").map(baseDirectory.value / "base/subdir/nested-subdir" / _)
  assert((all / inputs).value.all.toSet == expected)
}

val set = taskKey[Seq[File]]("Specify redundant sources in a set")

set / inputs ++= Seq(
  baseDirectory.value / "base" ** -DirectoryFilter,
  baseDirectory.value / "base" / "subdir" / "nested-subdir" * -DirectoryFilter
)

val checkSet = taskKey[Unit]("Verify that redundant sources are handled")

checkSet := {
  val redundant = (set / inputs).value.all
  assert(redundant.size == 4) // It should get Foo.txt and Bar.md twice

  val deduped = (set / inputs).value.toSet[Glob].all
  val expected = Seq("Bar.md", "Foo.txt").map(baseDirectory.value / "base/subdir/nested-subdir" / _)
  assert(deduped.sorted == expected)

  val altDeduped = (set / inputs).value.unique
  assert(altDeduped.sorted == expected)
}

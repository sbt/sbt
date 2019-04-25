// The project contains two files: { Foo.txt, Bar.md } in the subdirector base/subdir/nested-subdir

// Check that we can correctly extract Foo.txt with a recursive source
val foo = taskKey[Seq[File]]("Retrieve Foo.txt")

foo / fileInputs += baseDirectory.value ** "*.txt"

foo := (foo / allInputPaths).value.map(_.toFile)

val checkFoo = taskKey[Unit]("Check that the Foo.txt file is retrieved")

checkFoo := assert(foo.value == Seq(baseDirectory.value / "base/subdir/nested-subdir/Foo.txt"))

// Check that we can correctly extract Bar.md with a non-recursive source
val bar = taskKey[Seq[File]]("Retrieve Bar.md")

bar / fileInputs += baseDirectory.value / "base/subdir/nested-subdir" * "*.md"

bar := (bar / allInputPaths).value.map(_.toFile)

val checkBar = taskKey[Unit]("Check that the Bar.md file is retrieved")

checkBar := assert(bar.value == Seq(baseDirectory.value / "base/subdir/nested-subdir/Bar.md"))

// Check that we can correctly extract Bar.md and Foo.md with a non-recursive source
val all = taskKey[Seq[File]]("Retrieve all files")

all / fileInputs += baseDirectory.value / "base" / "subdir" / "nested-subdir" * AllPassFilter

val checkAll = taskKey[Unit]("Check that the Bar.md file is retrieved")

checkAll := {
  import sbt.dsl.LinterLevel.Ignore
  val expected = Set("Foo.txt", "Bar.md").map(baseDirectory.value / "base/subdir/nested-subdir" / _)
  val actual = (all / allInputFiles).value.map(_.toFile).toSet
  assert(actual == expected)
}

val set = taskKey[Seq[File]]("Specify redundant sources in a set")

set / fileInputs ++= Seq(
  baseDirectory.value / "base" ** -DirectoryFilter,
  baseDirectory.value / "base" / "subdir" / "nested-subdir" * -DirectoryFilter
)

val depth = taskKey[Seq[File]]("Specify redundant sources with limited depth")
val checkDepth = taskKey[Unit]("Check that the Bar.md file is retrieved")

depth / fileInputs ++= {
  Seq(
    Glob(baseDirectory.value / "base", AnyPath / AnyPath / "*.md"),
    Glob(baseDirectory.value / "base" / "subdir", AnyPath / "*.md"),
  )
}

checkDepth := {
  val expected = Seq("Bar.md").map(baseDirectory.value / "base/subdir/nested-subdir" / _)
  val actual = (depth / allInputFiles).value.map(_.toFile)
  assert(actual == expected)
}

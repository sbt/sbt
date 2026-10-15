package sbt.librarymanagement

import hedgehog.*
import hedgehog.runner.*
import sjsonnew.support.murmurhash.Hasher

object ConfigurationHashSpec extends Properties:
  private val genName: Gen[String] =
    Gen.string(Gen.lower, Range.linear(1, 10))

  private def hash(c: Configuration): Int = Hasher.hashUnsafe(c)

  override def tests: List[Test] = List(
    property("hash ignores everything but the name", sameName),
    property("hash distinguishes names", differentNames),
    example(
      "hash matches for Compile and a redefinition named compile",
      Result.assert(
        hash(Configurations.Compile) == hash(
          Configuration.of("CustomCompile", "compile").extend(Configurations.Test).hide
        )
      )
    ),
  )

  def sameName: Property =
    for
      name <- genName.forAll
      description <- Gen.string(Gen.alphaNum, Range.linear(0, 10)).forAll
      isPublic <- Gen.boolean.forAll
      transitive <- Gen.boolean.forAll
    yield
      val base = Configuration.of("Base", name)
      val other = Configuration.of(
        "Other",
        name,
        description,
        isPublic,
        Vector(Configurations.Compile),
        transitive
      )
      Result.assert(hash(base) == hash(other))

  def differentNames: Property =
    for
      a <- genName.forAll
      b <- genName.filter(_ != a).forAll
    yield Result.assert(hash(Configuration.of("A", a)) != hash(Configuration.of("A", b)))
end ConfigurationHashSpec

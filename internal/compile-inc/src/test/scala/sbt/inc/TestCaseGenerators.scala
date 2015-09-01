package sbt
package inc

import java.io.File

import org.scalacheck._
import Arbitrary._
import Gen._

import sbt.Relation
import xsbti.api._
import xsbti.SafeLazy
import xsbti.DependencyContext._

/**
 * Scalacheck generators for Analysis objects and their substructures.
 * Fairly complex, as Analysis has interconnected state that can't be
 * independently generated.
 */
object TestCaseGenerators {
  // We restrict sizes, otherwise the generated Analysis objects get huge and the tests take a long time.
  val maxSources = 10 // Max number of source files.
  val maxRelatives = 10 // Max number of things that a source x can relate to in a single Relation.
  val maxPathSegmentLen = 10 // Max number of characters in a path segment.
  val maxPathLen = 6 // Max number of path segments in a path.

  // Ensure that we generate unique class names and file paths every time.
  // Using repeated strings may lead to all sorts of undesirable interactions.
  val used1 = scala.collection.mutable.Set.empty[String]
  val used2 = scala.collection.mutable.Set.empty[String]

  // When using `retryUntil`, the condition is actually tested twice (see implementation in ScalaCheck),
  // which is why we need to insert twice the element.
  // If the element is present in both sets, then it has already been used.
  def unique[T](g: Gen[T]) = g retryUntil { o: T =>
    if (used1.add(o.toString))
      true
    else
      used2.add(o.toString)
  }

  def identifier: Gen[String] = sized { size =>
    resize(Math.max(size, 3), Gen.identifier)
  }

  def genFilePathSegment: Gen[String] = for {
    n <- choose(3, maxPathSegmentLen) // Segments have at least 3 characters.
    c <- alphaChar
    cs <- listOfN(n - 1, alphaNumChar)
  } yield (c :: cs).mkString

  def genFile: Gen[File] = for {
    n <- choose(2, maxPathLen) // Paths have at least 2 segments.
    path <- listOfN(n, genFilePathSegment)
  } yield new File(path.mkString("/"))

  def genStamp: Gen[Stamp] = for {
    b <- oneOf(true, false)
  } yield new Exists(b)

  def zipMap[A, B](a: Seq[A], b: Seq[B]): Map[A, B] = (a zip b).toMap

  def genStamps(rel: Relations): Gen[Stamps] = {
    val prod = rel.allProducts.toList
    val src = rel.allSources.toList
    val bin = rel.allBinaryDeps.toList
    for {
      prodStamps <- listOfN(prod.length, genStamp)
      srcStamps <- listOfN(src.length, genStamp)
      binStamps <- listOfN(bin.length, genStamp)
      binClassNames <- listOfN(bin.length, unique(identifier))
    } yield Stamps(zipMap(prod, prodStamps), zipMap(src, srcStamps), zipMap(bin, binStamps), zipMap(bin, binClassNames))
  }

  // We need "proper" definitions with specific class names, as groupBy use these to pick a representative top-level class when splitting.
  private[this] def makeDefinition(name: String): Definition =
    new ClassLike(DefinitionType.ClassDef, lzy(new EmptyType()),
      lzy(new Structure(lzy(Array()), lzy(Array()), lzy(Array()))), Array(), Array(),
      name, new Public(), new Modifiers(false, false, false, false, false, false, false), Array())

  private[this] def lzy[T <: AnyRef](x: T) = SafeLazy.strict(x)

  def genNameHash(defn: String): Gen[xsbti.api._internalOnly_NameHash] =
    const(new xsbti.api._internalOnly_NameHash(defn, defn.hashCode()))

  def genNameHashes(defns: Seq[String]): Gen[xsbti.api._internalOnly_NameHashes] = {
    def partitionAccordingToMask[T](mask: List[Boolean], xs: List[T]): (List[T], List[T]) = {
      val (p1, p2) = (mask zip xs).partition(_._1)
      (p1.map(_._2), p2.map(_._2))
    }
    val pairsOfGenerators = for (defn <- defns) yield {
      for {
        isRegularMember <- arbitrary[Boolean]
        nameHash <- genNameHash(defn)
      } yield (isRegularMember, nameHash)
    }
    val genNameHashesList = Gen.sequence[List, xsbti.api._internalOnly_NameHash](defns.map(genNameHash))
    val genTwoListOfNameHashes = for {
      nameHashesList <- genNameHashesList
      isRegularMemberList <- listOfN(nameHashesList.length, arbitrary[Boolean])
    } yield partitionAccordingToMask(isRegularMemberList, nameHashesList)
    for {
      (regularMemberNameHashes, implicitMemberNameHashes) <- genTwoListOfNameHashes
    } yield new xsbti.api._internalOnly_NameHashes(regularMemberNameHashes.toArray, implicitMemberNameHashes.toArray)
  }

  def genSource(defns: Seq[String]): Gen[Source] = for {
    startTime <- arbitrary[Long]
    hashLen <- choose(10, 20) // Requred by SameAPI to be > 0.
    hash <- Gen.containerOfN[Array, Byte](hashLen, arbitrary[Byte])
    apiHash <- arbitrary[Int]
    hasMacro <- arbitrary[Boolean]
    nameHashes <- genNameHashes(defns)
  } yield new Source(new Compilation(startTime, Array()), hash, new SourceAPI(Array(), Array(defns map makeDefinition: _*)), apiHash, nameHashes, hasMacro)

  def genSources(all_defns: Seq[Seq[String]]): Gen[Seq[Source]] = Gen.sequence[List, Source](all_defns.map(genSource))

  def genAPIs(rel: Relations): Gen[APIs] = {
    val internal = rel.allInternalSrcDeps.toList.sorted
    val external = rel.allExternalDeps.toList.sorted
    for {
      internalSources <- genSources(internal map { f: File => rel.classNames(f).toList.sorted })
      externalSources <- genSources(external map { s: String => s :: Nil })
    } yield APIs(zipMap(internal, internalSources), zipMap(external, externalSources))
  }

  def genRelation[T](g: Gen[T])(srcs: List[File]): Gen[Relation[File, T]] = for {
    n <- choose(1, maxRelatives)
    entries <- listOfN(srcs.length, containerOfN[Set, T](n, g))
  } yield Relation.reconstruct(zipMap(srcs, entries))

  val genFileRelation = genRelation[File](unique(genFile)) _
  val genStringRelation = genRelation[String](unique(identifier)) _

  def genRSource(srcs: List[File]): Gen[Relations.Source] = for {
    internal <- listOfN(srcs.length, someOf(srcs)) // Internal dep targets must come from list of sources.
    external <- genStringRelation(srcs)
  } yield Relations.makeSource( // Ensure that we don't generate a dep of some file on itself.
    Relation.reconstruct((srcs zip (internal map { _.toSet }) map { case (a, b) => (a, b - a) }).toMap),
    external)

  def genSubRSource(src: Relations.Source): Gen[Relations.Source] = for {
    internal <- someOf(src.internal.all.toList)
    external <- someOf(src.external.all.toList)
  } yield Relations.makeSource(Relation.empty ++ internal, Relation.empty ++ external)

  def genRSourceDependencies(srcs: List[File]): Gen[Relations.SourceDependencies] = for {
    internal <- listOfN(srcs.length, someOf(srcs))
    external <- genStringRelation(srcs)
  } yield Relations.makeSourceDependencies(
    Relation.reconstruct((srcs zip (internal map { _.toSet }) map { case (a, b) => (a, b - a) }).toMap),
    external)

  def genSubRSourceDependencies(src: Relations.SourceDependencies): Gen[Relations.SourceDependencies] = for {
    internal <- someOf(src.internal.all.toList)
    external <- someOf(src.external.all.toList)
  } yield Relations.makeSourceDependencies(Relation.empty ++ internal, Relation.empty ++ external)

  def genRelations: Gen[Relations] = for {
    numSrcs <- choose(0, maxSources)
    srcs <- listOfN(numSrcs, genFile)
    srcProd <- genFileRelation(srcs)
    binaryDep <- genFileRelation(srcs)
    direct <- genRSource(srcs)
    publicInherited <- genSubRSource(direct)
    classes <- genStringRelation(srcs)

  } yield Relations.make(srcProd, binaryDep, direct, publicInherited, classes)

  def genRelationsNameHashing: Gen[Relations] = for {
    numSrcs <- choose(0, maxSources)
    srcs <- listOfN(numSrcs, genFile)
    srcProd <- genFileRelation(srcs)
    binaryDep <- genFileRelation(srcs)
    memberRef <- genRSourceDependencies(srcs)
    inheritance <- genSubRSourceDependencies(memberRef)
    classes <- genStringRelation(srcs)
    names <- genStringRelation(srcs)
    internal <- InternalDependencies(Map(DependencyByMemberRef -> memberRef.internal, DependencyByInheritance -> inheritance.internal))
    external <- ExternalDependencies(Map(DependencyByMemberRef -> memberRef.external, DependencyByInheritance -> inheritance.external))
  } yield Relations.make(srcProd, binaryDep, internal, external, classes, names)

  def genAnalysis(nameHashing: Boolean): Gen[Analysis] = for {
    rels <- if (nameHashing) genRelationsNameHashing else genRelations
    stamps <- genStamps(rels)
    apis <- genAPIs(rels)
  } yield new MAnalysis(stamps, apis, rels, SourceInfos.empty, Compilations.empty)
}

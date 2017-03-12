package sbt.inc

import sbt.Relation
import java.io.File
import sbt.Logger
import xsbt.api.APIUtil

/**
  * Implements various strategies for invalidating dependencies introduced by member reference.
  *
  * The strategy is represented as function T => Set[File] where T is a source file that other
  * source files depend on. When you apply that function to given element `src` you get set of
  * files that depend on `src` by member reference and should be invalidated due to api change
  * that was passed to a method constructing that function. There are two questions that arise:
  *
  *    1. Why is signature T => Set[File] and not T => Set[T] or File => Set[File]?
  *    2. Why would we apply that function to any other `src` that then one that got modified
  *       and the modification is described by APIChange?
  *
  * Let's address the second question with the following example of source code structure:
  *
  * // A.scala
  * class A
  *
  * // B.scala
  * class B extends A
  *
  * // C.scala
  * class C { def foo(a: A) = ??? }
  *
  * // D.scala
  * class D { def bar(b: B) = ??? }
  *
  * Member reference dependencies on A.scala are B.scala, C.scala. When the api of A changes
  * then we would consider B and C for invalidation. However, B is also a dependency by inheritance
  * so we always invalidate it. The api change to A is relevant when B is considered (because
  * of how inheritance works) so we would invalidate B by inheritance and then we would like to
  * invalidate member reference dependencies of B as well. In other words, we have a function
  * because we want to apply it (with the same api change in mind) to all src files invalidated
  * by inheritance of the originally modified file.
  *
  * The first question is a bit more straightforward to answer. We always invalidate internal
  * source files (in given project) that are represented as File but they might depend either on
  * internal source files (then T=File) or they can depend on external class name (then T=String).
  *
  * The specific invalidation strategy is determined based on APIChange that describes a change to api
  * of a single source file.
  *
  * For example, if we get APIChangeDueToMacroDefinition then we invalidate all member reference
  * dependencies unconditionally. On the other hand, if api change is due to modified name hashes
  * of regular members then we'll invalidate sources that use those names.
  */
private[inc] class MemberRefInvalidator(log: Logger, logRecompileOnMacro: Boolean) {
  def get[T](memberRef: Relation[File, T],
             usedNames: Relation[File, String],
             apiChange: APIChange[_]): T => Set[File] = apiChange match {
    case _: APIChangeDueToMacroDefinition[_] =>
      new InvalidateDueToMacroDefinition(memberRef)
    case NamesChange(_, modifiedNames) if modifiedNames.implicitNames.nonEmpty =>
      new InvalidateUnconditionally(memberRef)
    case NamesChange(modifiedSrcFile, modifiedNames) =>
      new NameHashFilteredInvalidator[T](usedNames, memberRef, modifiedNames.regularNames)
    case _: SourceAPIChange[_] =>
      sys.error(wrongAPIChangeMsg)
  }

  def invalidationReason(apiChange: APIChange[_]): String = apiChange match {
    case APIChangeDueToMacroDefinition(modifiedSrcFile) =>
      s"The $modifiedSrcFile source file declares a macro."
    case NamesChange(modifiedSrcFile, modifiedNames) if modifiedNames.implicitNames.nonEmpty =>
      s"""|The $modifiedSrcFile source file has the following implicit definitions changed:
				|\t${modifiedNames.implicitNames.mkString(", ")}.""".stripMargin
    case NamesChange(modifiedSrcFile, modifiedNames) =>
      s"""|The $modifiedSrcFile source file has the following regular definitions changed:
				|\t${modifiedNames.regularNames.mkString(", ")}.""".stripMargin
    case _: SourceAPIChange[_] =>
      sys.error(wrongAPIChangeMsg)
  }

  private val wrongAPIChangeMsg =
    "MemberReferenceInvalidator.get should be called when name hashing is enabled " +
      "and in that case we shouldn't have SourceAPIChange as an api change."

  private class InvalidateDueToMacroDefinition[T](memberRef: Relation[File, T]) extends (T => Set[File]) {
    def apply(from: T): Set[File] = {
      val invalidated = memberRef.reverse(from)
      if (invalidated.nonEmpty && logRecompileOnMacro) {
        log.info(
          s"Because $from contains a macro definition, the following dependencies are invalidated unconditionally:\n" +
            formatInvalidated(invalidated)
        )
      }
      invalidated
    }
  }

  private class InvalidateUnconditionally[T](memberRef: Relation[File, T]) extends (T => Set[File]) {
    def apply(from: T): Set[File] = {
      val invalidated = memberRef.reverse(from)
      if (invalidated.nonEmpty) {
        log.debug(
          s"The following member ref dependencies of $from are invalidated:\n" +
            formatInvalidated(invalidated)
        )
      }
      invalidated
    }
  }

  private def formatInvalidated(invalidated: Set[File]): String = {
    val sortedFiles = invalidated.toSeq.sortBy(_.getAbsolutePath)
    sortedFiles.map(file => "\t" + file).mkString("\n")
  }

  private class NameHashFilteredInvalidator[T](usedNames: Relation[File, String],
                                               memberRef: Relation[File, T],
                                               modifiedNames: Set[String])
      extends (T => Set[File]) {

    def apply(to: T): Set[File] = {
      val dependent = memberRef.reverse(to)
      filteredDependencies(dependent)
    }
    private def filteredDependencies(dependent: Set[File]): Set[File] =
      dependent.filter {
        case from if APIUtil.isScalaSourceName(from.getName) =>
          val usedNamesInDependent = usedNames.forward(from)
          val modifiedAndUsedNames = modifiedNames intersect usedNamesInDependent
          if (modifiedAndUsedNames.isEmpty) {
            log.debug(
              s"None of the modified names appears in $from. This dependency is not being considered for invalidation."
            )
            false
          } else {
            log.debug(s"The following modified names cause invalidation of $from: $modifiedAndUsedNames")
            true
          }
        case from =>
          log.debug(s"Name hashing optimization doesn't apply to non-Scala dependency: $from")
          true
      }
  }
}

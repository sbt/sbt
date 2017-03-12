/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt

import java.io.File
import java.util.regex.Pattern

/** A `java.io.FileFilter` with additional methods for combining filters. */
trait FileFilter extends java.io.FileFilter with NotNull {

  /** Constructs a filter that accepts a `File` if it matches either this filter or the given `filter`. */
  def ||(filter: FileFilter): FileFilter = new SimpleFileFilter(file => accept(file) || filter.accept(file))

  /** Constructs a filter that accepts a `File` if it matches both this filter and the given `filter`. */
  def &&(filter: FileFilter): FileFilter = new SimpleFileFilter(file => accept(file) && filter.accept(file))

  /** Constructs a filter that accepts a `File` if it matches this filter but does not match the given `filter`. */
  def --(filter: FileFilter): FileFilter = new SimpleFileFilter(file => accept(file) && !filter.accept(file))

  /** Constructs a filter that accepts a `File` if it does not match this filter. */
  def unary_- : FileFilter = new SimpleFileFilter(file => !accept(file))
}

/** A filter on Strings.  This also functions as a [[FileFilter]] by applying the String filter to the value of a File's `getName`. */
trait NameFilter extends FileFilter with NotNull {

  /** Returns `true` to include the `name`, `false` to exclude it. */
  def accept(name: String): Boolean

  /** Accepts `File` if its `getName` method is accepted by this filter. */
  final def accept(file: File): Boolean = accept(file.getName)

  /** Constructs a filter that accepts a `String` if it matches either this filter or the given `filter`. */
  def |(filter: NameFilter): NameFilter = new SimpleFilter(name => accept(name) || filter.accept(name))

  /** Constructs a filter that accepts a `String` if it matches both this filter and the given `filter`. */
  def &(filter: NameFilter): NameFilter = new SimpleFilter(name => accept(name) && filter.accept(name))

  /** Constructs a filter that accepts a `String` if it matches this filter but not the given `filter`. */
  def -(filter: NameFilter): NameFilter = new SimpleFilter(name => accept(name) && !filter.accept(name))

  /** Constructs a filter that accepts a `String` if it does not match this filter. */
  override def unary_- : NameFilter = new SimpleFilter(name => !accept(name))
}

/** A [[FileFilter]] that selects files that are hidden according to `java.io.File.isHidden` or if they start with a dot (`.`). */
object HiddenFileFilter extends FileFilter {
  def accept(file: File) = file.isHidden && file.getName != "."
}

/** A [[FileFilter]] that selects files that exist according to `java.io.File.exists`. */
object ExistsFileFilter extends FileFilter {
  def accept(file: File) = file.exists
}

/** A [[FileFilter]] that selects files that are a directory according to `java.io.File.isDirectory`. */
object DirectoryFilter extends FileFilter {
  def accept(file: File) = file.isDirectory
}

/** A [[FileFilter]] that selects files according the predicate `acceptFunction`. */
final class SimpleFileFilter(val acceptFunction: File => Boolean) extends FileFilter {
  def accept(file: File) = acceptFunction(file)
}

/** A [[NameFilter]] that accepts a name if it is exactly equal to `matchName`. */
final class ExactFilter(val matchName: String) extends NameFilter {
  def accept(name: String) = matchName == name
}

/** A [[NameFilter]] that accepts a name if the predicate `acceptFunction` accepts it. */
final class SimpleFilter(val acceptFunction: String => Boolean) extends NameFilter {
  def accept(name: String) = acceptFunction(name)
}

/** A [[NameFilter]] that accepts a name if it matches the regular expression defined by `pattern`. */
final class PatternFilter(val pattern: Pattern) extends NameFilter {
  def accept(name: String) = pattern.matcher(name).matches
}

/** A [[NameFilter]] that accepts all names. That is, `accept` always returns `true`. */
object AllPassFilter extends NameFilter {
  def accept(name: String) = true
}

/** A [[NameFilter]] that accepts nothing.  That is, `accept` always returns `false`. */
object NothingFilter extends NameFilter {
  def accept(name: String) = false
}

object NameFilter {
  implicit def fnToNameFilter(f: String => Boolean): NameFilter = new NameFilter {
    def accept(name: String) = f(name)
  }
}
object FileFilter {

  /** Allows a String to be used where a `NameFilter` is expected and any asterisks (`*`) will be interpreted as wildcards.  See [[sbt.GlobFilter]].*/
  implicit def globFilter(s: String): NameFilter = GlobFilter(s)
}

/** Constructs a filter from a String, interpreting wildcards.  See the [[apply]] method. */
object GlobFilter {

  /**
    * Constructs a [[NameFilter]] from a String, interpreting `*` as a wildcard.
    * Control characters, as determined by `java.lang.Character.isISOControl` are not allowed
    * due to the implementation restriction of using Java's `Pattern` and `Pattern.quote`,
    * which do not handle these characters.
    */
  def apply(expression: String): NameFilter = {
    require(
      !expression.exists(java.lang.Character.isISOControl),
      "Control characters not allowed in filter expression."
    )
    if (expression == "*")
      AllPassFilter
    else if (expression.indexOf('*') < 0) // includes case where expression is empty
      new ExactFilter(expression)
    else
      new PatternFilter(Pattern.compile(expression.split("\\*", -1).map(quote).mkString(".*")))
  }
  private def quote(s: String) = if (s.isEmpty) "" else Pattern.quote(s.replaceAll("\n", """\n"""))
}

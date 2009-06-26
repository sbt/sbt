/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package sbt

import java.io.File
import java.util.regex.Pattern

trait FileFilter extends java.io.FileFilter with NotNull
{
	def || (filter: FileFilter): FileFilter = new SimpleFileFilter( file => accept(file) || filter.accept(file) )
	def && (filter: FileFilter): FileFilter = new SimpleFileFilter( file => accept(file) && filter.accept(file) )
	def -- (filter: FileFilter): FileFilter = new SimpleFileFilter( file => accept(file) && !filter.accept(file) )
	def unary_- : FileFilter = new SimpleFileFilter( file => !accept(file) )
}
trait NameFilter extends FileFilter with NotNull
{
	def accept(name: String): Boolean
	final def accept(file: File): Boolean = accept(file.getName)
	def | (filter: NameFilter): NameFilter = new SimpleFilter( name => accept(name) || filter.accept(name) )
	def & (filter: NameFilter): NameFilter = new SimpleFilter( name => accept(name) && filter.accept(name) )
	def - (filter: NameFilter): NameFilter = new SimpleFilter( name => accept(name) && !filter.accept(name) )
	override def unary_- : NameFilter = new SimpleFilter( name => !accept(name) )
}
object HiddenFileFilter extends FileFilter {
	def accept(file: File) = file.isHidden && file.getName != "."
}
object ExistsFileFilter extends FileFilter {
	def accept(file: File) = file.exists
}
object DirectoryFilter extends FileFilter {
	def accept(file: File) = file.isDirectory
}
class SimpleFileFilter(val acceptFunction: File => Boolean) extends FileFilter
{
	def accept(file: File) = acceptFunction(file)
}
class ExactFilter(val matchName: String) extends NameFilter
{
	def accept(name: String) = matchName == name
}
class SimpleFilter(val acceptFunction: String => Boolean) extends NameFilter
{
	def accept(name: String) = acceptFunction(name)
}
class PatternFilter(val pattern: Pattern) extends NameFilter
{
	def accept(name: String) = pattern.matcher(name).matches
}
object AllPassFilter extends NameFilter
{
	def accept(name: String) = true
}
object NothingFilter extends NameFilter
{
	def accept(name: String) = false
}

object GlobFilter
{
	def apply(expression: String): NameFilter =
	{
		require(!expression.exists(java.lang.Character.isISOControl), "Control characters not allowed in filter expression.")
		if(expression == "*")
			AllPassFilter
		else if(expression.indexOf('*') < 0) // includes case where expression is empty
			new ExactFilter(expression)
		else
			new PatternFilter(Pattern.compile(expression.split("\\*", -1).map(quote).mkString(".*")))
	}
	private def quote(s: String) = if(s.isEmpty) "" else Pattern.quote(s.replaceAll("\n", """\n"""))
}
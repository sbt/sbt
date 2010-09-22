/* sbt -- Simple Build Tool
 * Copyright  2010 Mark Harrah
 */
package object sbt extends sbt.std.TaskExtra with sbt.Types with sbt.ProcessExtra with sbt.impl.DependencyBuilders
{
	type File = java.io.File
}

/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.datatype

import java.io.File

/** Generates a datatype hierarchy from a definition file.*/
object GenerateDatatypes
{
	/** Arguments: ('mutable' | 'immutable') <base package name> <base directory> <input file>+*/
	def main(args: Array[String])
	{
		if(args.length < 4)
		{
			System.err.println("Invalid number of arguments, expected 'mutable' or 'immutable', package, base directory, and files to process")
			System.exit(1)
		}
		else
		{
			val immutable = args(0).trim.toLowerCase == "immutable"

			val packageName = args(1).trim
			require(!packageName.isEmpty)

			val baseDirectory = new File(args(2))
			baseDirectory.mkdirs

			val files = args.drop(3).map(new File(_))
			// parse the files, getting all interface and enums
			val parser = new DatatypeParser
			val definitions = files.flatMap(parser.parseFile)

			// create the interfaces, enums, and class implementations
			val generator = if(immutable) new ImmutableGenerator(packageName, baseDirectory) else new MutableGenerator(packageName, baseDirectory)
			generator.writeDefinitions(definitions)
		}
	}
}

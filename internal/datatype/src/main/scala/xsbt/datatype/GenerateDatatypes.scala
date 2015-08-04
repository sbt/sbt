/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package xsbt.datatype

import java.io.File
import java.util.Locale

/** Generates a datatype hierarchy from a definition file.*/
object GenerateDatatypes {
  /** Arguments: <base package name> <base directory> <input file>+*/
  def main(args: Array[String]): Unit = {
    if (args.length < 3) {
      System.err.println("Invalid number of arguments, expected package, base directory, and files to process")
      System.exit(1)
    } else {
      val packageName = args(0).trim
      require(!packageName.isEmpty)

      val baseDirectory = new File(args(1))
      baseDirectory.mkdirs

      val files = args.drop(2).map(new File(_))
      // parse the files, getting all interface and enums
      val parser = new DatatypeParser
      val definitions = files.flatMap(parser.parseFile)

      // create the interfaces, enums, and class implementations
      val generator = new ImmutableGenerator(packageName, baseDirectory)
      generator.writeDefinitions(definitions)
    }
  }
}

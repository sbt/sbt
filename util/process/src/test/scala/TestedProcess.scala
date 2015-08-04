package sbt

import java.io.{ File, FileNotFoundException, IOException }

object exit {
  def main(args: Array[String]): Unit = {
    System.exit(java.lang.Integer.parseInt(args(0)))
  }
}
object cat {
  def main(args: Array[String]): Unit = {
    try {
      if (args.length == 0)
        IO.transfer(System.in, System.out)
      else
        catFiles(args.toList)
      System.exit(0)
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        System.err.println("Error: " + e.toString)
        System.exit(1)
    }
  }
  private def catFiles(filenames: List[String]): Option[String] =
    {
      filenames match {
        case head :: tail =>
          val file = new File(head)
          if (file.isDirectory)
            throw new IOException("Is directory: " + file)
          else if (file.exists) {
            Using.fileInputStream(file) { stream =>
              IO.transfer(stream, System.out)
            }
            catFiles(tail)
          } else
            throw new FileNotFoundException("No such file or directory: " + file)
        case Nil => None
      }
    }
}
object echo {
  def main(args: Array[String]): Unit =
    System.out.println(args.mkString(" "))
}

package t

import java.nio._, charset._, file._

object Main {
  def main(args: Array[String]): Unit = {
    println(new String(Files.readAllBytes(Paths.get(getClass().getResource("/a.txt").toURI()))))
  }
}
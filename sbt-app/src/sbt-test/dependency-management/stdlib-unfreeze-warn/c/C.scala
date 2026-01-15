import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets

object C {
  def main(args: Array[String]): Unit = {
    assert(scala.collection.immutable.Exp.v == null)
    Files.write(Paths.get(s"s${scala.util.Properties.versionNumberString}.txt"), "nix".getBytes)
  }
}

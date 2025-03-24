import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets

object B extends App {
  println(AMacro.m(33)) // fails
  Files.write(Paths.get(s"s${scala.util.Properties.versionNumberString}.txt"), "nix".getBytes)
}

import java.nio.file.{Paths, Files}
import java.nio.charset.StandardCharsets

@main def hubu =
  Mac.inspect(println("hai"))
  Files.write(Paths.get(s"s${scala.util.Properties.versionNumberString}.txt"), "nix".getBytes)

import java.io.File
import java.nio.file.Files

@main def hello() =
  Files.writeString(new File("output").toPath, "OK")

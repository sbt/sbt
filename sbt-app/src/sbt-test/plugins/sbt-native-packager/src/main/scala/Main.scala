import java.io.File
import java.nio.file.Files

@main def hello() =
  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))

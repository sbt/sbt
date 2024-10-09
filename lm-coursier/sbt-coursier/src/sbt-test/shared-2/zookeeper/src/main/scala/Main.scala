import java.io.File
import java.nio.file.Files

import org.apache.zookeeper.ZooKeeper

object Main extends App {
  Files.write(new File("output").toPath, classOf[ZooKeeper].getSimpleName.getBytes("UTF-8"))
}

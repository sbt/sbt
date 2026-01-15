import java.io.File
import java.nio.file.Files

import org.apache.zookeeper.ZooKeeper

object Main {
  def main(args: Array[String]): Unit = {
    Files.write(new File("output").toPath, classOf[ZooKeeper].getSimpleName.getBytes("UTF-8"))
  }
}

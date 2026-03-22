// Test for issue #7152 - custom appenders using public API

import sbt._
import sbt.Keys._
import java.io.{ File, PrintWriter }

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.13.12",
    
    // Test that we can use extraAppenders with public API only
    extraAppenders := {
      new AppenderSupplier {
        def apply(key: ScopedKey[_]): Seq[Appender] = {
          // Create custom appender that writes to a file for verification
          val outputFile = new File("target/appender-output.txt")
          outputFile.getParentFile.mkdirs()
          val fileWriter = new PrintWriter(outputFile)
          
          val customAppender = Appenders.consoleAppender(
            s"test-${key.key.label}",
            new PrintWriter(System.out) {
              override def println(msg: String): Unit = {
                val prefixed = s"[CUSTOM:${key.key.label}] $msg"
                super.println(prefixed)
                fileWriter.println(prefixed)
                fileWriter.flush()
              }
            }
          )
          Seq(customAppender)
        }
      }
    },
    
    TaskKey[Unit]("checkLogging") := {
      streams.value.log.info("Test message from checkLogging task")
    },
    
    TaskKey[Unit]("verifyPublicAPI") := {
      // Verify we can create appenders using only public API
      val appender1 = Appenders.consoleAppender()
      val appender2 = Appenders.consoleAppender("test-appender")
      val appender3 = Appenders.consoleAppender(new PrintWriter(System.out))
      streams.value.log.info("Successfully created appenders using public API")
    },
    
    TaskKey[Unit]("verifyMultipleTasks") := {
      streams.value.log.info("Message from verifyMultipleTasks")
    }
  )

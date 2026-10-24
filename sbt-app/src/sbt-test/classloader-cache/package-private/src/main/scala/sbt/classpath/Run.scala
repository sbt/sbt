package com.typesafe.config.impl

object Run {
  def main(args: Array[String]): Unit = {
    // Token is package private
    // https://github.com/lightbend/config/blob/a9e5a9d1c6c9e73a5/config/src/main/java/com/typesafe/config/impl/Token.java#L9
    println(classOf[com.typesafe.config.impl.Token])
  }
}

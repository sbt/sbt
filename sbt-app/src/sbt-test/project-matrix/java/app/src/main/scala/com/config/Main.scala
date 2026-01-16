package com.config

object Main {
  def main(args: Array[String]): Unit = {
    println(s"Version: ${MyClass.configValue()}")
  }
}

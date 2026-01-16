package com.example

object App {
  def main(args: Array[String]): Unit = {
    println(s"Base version: ${Base.version}")
    println(s"Middle uses: ${Middle.useBase()}")
  }
}

// https://github.com/sbt/sbt/issues/7768

val a1 = settingKey[Seq[Int]]("")
val a2 = settingKey[Int]("")
val a3 = settingKey[Int]("")
val a4 = settingKey[Int]("")
val a5 = settingKey[Int]("")
val a6 = settingKey[Int]("")
val a7 = settingKey[Int]("")
val a8 = settingKey[Int]("")
val a9 = settingKey[Int]("")
val a10 = settingKey[Int]("")
val a11 = settingKey[Int]("")
val a12 = settingKey[Int]("")
val a13 = settingKey[Int]("")
val a14 = settingKey[Int]("")
val a15 = settingKey[Int]("")
val a16 = settingKey[Int]("")
val a17 = settingKey[Int]("")
val a18 = settingKey[Int]("")
val a19 = settingKey[Int]("")
val a20 = settingKey[Int]("")
val a21 = settingKey[Int]("")
val a22 = settingKey[Int]("")
val a23 = settingKey[Int]("")

a1 := List(1)
a2 := 2
a3 := 3
a4 := 4
a5 := 5
a6 := 6
a7 := 7
a8 := 8
a9 := 9
a10 := 10
a11 := 11
a12 := 12
a13 := 13
a14 := 14
a15 := 15
a16 := 16
a17 := 17
a18 := 18
a19 := 19
a20 := 20
a21 := 21
a22 := 22
a23 := 23

TaskKey[Unit]("check") := {
  val sum = (
    a1.value ++ List(
      a2.value,
      a3.value,
      a4.value,
      a5.value,
      a6.value,
      a7.value,
      a8.value,
      a9.value,
      a10.value,
      a11.value,
      a12.value,
      a13.value,
      a14.value,
      a15.value,
      a16.value,
      a17.value,
      a18.value,
      a19.value,
      a20.value,
      a21.value,
      a22.value,
      a23.value,
    )
  ).sum
  assert(sum == 276, sum)
}

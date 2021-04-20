// https://github.com/sbt/sbt/issues/1673#issuecomment-537327439

val Config_0 = config("config-0").extend(Compile)
val Config_1 = config("config-1").extend(Compile)
val Config_2 = config("config-2").extend(Compile)
val Config_3 = config("config-3").extend(Compile)
val Config_4 = config("config-4").extend(Compile)
val Config_5 = config("config-5").extend(Compile)
val Config_6 = config("config-6").extend(Compile)
val Config_7 = config("config-7").extend(Compile)
val Config_8 = config("config-8").extend(Compile)
val Config_9 = config("config-9").extend(Compile)
val Config_10 = config("config-10").extend(Compile)
val Config_11 = config("config-11").extend(Compile)
val Config_12 = config("config-12").extend(Compile)
val Config_13 = config("config-13").extend(Compile)
val Config_14 = config("config-14").extend(Compile)
val Config_15 = config("config-15").extend(Compile)

val CustomConfigs = List(Config_0, Config_1, Config_2, Config_3, Config_4, Config_5, Config_6, Config_7, Config_8, Config_9, Config_10, Config_11, Config_12, Config_13, Config_14, Config_15)

val t = taskKey[Unit]("")
val p1 = project
  .configs(CustomConfigs: _*)
  .settings(
    t := {
        (compile in Config_0).value
        (compile in Config_1).value
        (compile in Config_2).value
        (compile in Config_3).value
        (compile in Config_4).value
        (compile in Config_5).value
        (compile in Config_6).value
        (compile in Config_7).value
        (compile in Config_8).value
        (compile in Config_9).value
        (compile in Config_10).value
        (compile in Config_11).value
        (compile in Config_12).value
        (compile in Config_13).value
        (compile in Config_14).value
        (compile in Config_15).value
    }
  )
  .settings(CustomConfigs.flatMap(c => inConfig(c)(Defaults.testSettings)))

echo build using JDK 8, test using JDK 8, on Windows
cd launcher-package
bin\coursier.bat resolve || exit /b
sbt -Dsbt.build.version=$TEST_SBT_VER universal:packageBin universal:stage integrationTest/test || exit /b
cd citest
.\test.bat || exit /b
test3\test3.bat || exit /b
# see https://stackoverflow.com/questions/2224350/powershell-start-job-working-directory/2246542#2246542
Set-Location $args[0]
& java -jar -noverify coursier launch -r https://dl.bintray.com/scalaz/releases io.get-coursier:http-server-java7_2.11:1.0.0-SNAPSHOT -- -d tests/jvm/src/test/resources/test-repo/http/abc.com -u user -P pass -r realm --port 8081 -v

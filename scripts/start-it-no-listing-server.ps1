# see https://stackoverflow.com/questions/2224350/powershell-start-job-working-directory/2246542#2246542
Set-Location $args[0]
& java -jar -noverify coursier launch io.get-coursier:http-server_2.11:1.0.0-SNAPSHOT -- -d tests/jvm/src/test/resources/test-repo/http/abc.com -u user -P pass -r realm --port 8081 -v

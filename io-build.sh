cd ~
git clone https://github.com/eatkins/io.git io
cd io
git checkout origin/glob
sbt ';set version in ThisBuild := "1.3.0-glob-2"; publishLocal'

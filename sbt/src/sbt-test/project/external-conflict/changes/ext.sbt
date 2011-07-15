organization := "org.example"

name := "app"

version <<= baseDirectory { base =>
	if(base / "older" exists) "0.1.16" else "0.1.18"
}

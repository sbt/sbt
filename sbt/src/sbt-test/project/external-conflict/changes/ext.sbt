organization := "org.example"

name := "app"

version <<= baseDirectory { base =>
	if(base / "older" exists) "0.1.16" else "0.1.18"
}

ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / "../ivy-cache")))
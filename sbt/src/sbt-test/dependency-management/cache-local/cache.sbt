 ivyPaths <<= (baseDirectory, target)( (dir, t) => new IvyPaths(dir, Some(t / ".ivy2")))

simple_setting("name") is "test-project"

simple_task("zomg") is { println("ZOMG") }

simple_task("zomg2") on (name, version) is { (n,v) => println("ZOMG " + n + " = " + v + " !!!!!") }



### Add console task to help output

#### Fix for

https://github.com/sbt/sbt/issues/2254

#### Changes

##### State.scala
- state - added task help list to state object

##### Main.scala
- Initialise State with task help list

##### BasicCommands.scala
- incorporate task help list from state into help

##### Keys.scala
- Added list of help tasks
- To add other items to the list of help tasks, it's easy to modify the initialisation of helpList like this:

~~~
    val helpList = help(console) ++ help(compile)
~~~



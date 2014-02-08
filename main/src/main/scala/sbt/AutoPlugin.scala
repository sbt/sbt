/*
TODO:
- Natured type contains AutoPlugin and Nature
- atoms of AutoPlugin.select are Natured
- atoms of Project.natures are Nature
- no more AutoPlugin.provides: name comes from module name
- index all available AutoPlugins to get the tasks that will be added
- error message when a task doesn't exist that it would be provided by plugin x, enabled by natures y,z, blocked by a, b
*/
package sbt

	import Def.Setting
	import logic.Logic.LogicException

/** Marks a top-level object so that sbt will wildcard import it for .sbt files, `consoleProject`, and `set`. */
trait AutoImport

/**
An AutoPlugin defines a group of settings and the conditions where the settings are automatically added to a build (called "activation").
The `select` method defines the conditions,
  `provides` defines an identifier for the AutoPlugin,
  and a method like `projectSettings` defines the settings to add.

Steps for plugin authors:
1. Determine the [[Nature]]s that, when present (or absent), activate the AutoPlugin.
2. Determine the settings/configurations to automatically inject when activated.
3. Define a new, unique identifying [[Nature]] associated with the AutoPlugin, where a Nature is essentially a String ID.

For example, the following will automatically add the settings in `projectSettings`
  to a project that has both the `Web` and `Javascript` natures enabled.  It will itself
  define the `MyStuff` nature.  This nature can be explicitly disabled by the user to
  prevent the plugin from activating.

    object MyPlugin extends AutoPlugin {
        def select = Web && Javascript
        def provides = MyStuff
        override def projectSettings = Seq(...)
    }

Steps for users:
1. add dependencies on plugins as usual with addSbtPlugin
2. add Natures to Projects, which will automatically select the plugin settings to add for those Projects.

For example, given natures Web and Javascript (perhaps provided by plugins added with addSbtPlugin),

  <Project>.natures( Web && Javascript )

will activate `MyPlugin` defined above and have its settings automatically added.  If the user instead defines

  <Project>.natures( Web && Javascript && !MyStuff)

then the `MyPlugin` settings (and anything that activates only when `MyStuff` is activated) will not be added.
*/
abstract class AutoPlugin
{
	/** This AutoPlugin will be activated for a project when the [[Natures]] matcher returned by this method matches that project's natures
   * AND the user does not explicitly exclude the Nature returned by `provides`.
	*
	* For example, if this method returns `Web && Javascript`, this plugin instance will only be added
	* if the `Web` and `Javascript` natures are enabled. */
	def select: Natures

	/** The unique [[Nature]] for this AutoPlugin instance.  This has two purposes:
	* 1. The user can explicitly disable this AutoPlugin.
	* 2. Other plugins can activate based on whether this AutoPlugin was activated.
	*/
	def provides: Nature

	/** The [[Configuration]]s to add to each project that activates this AutoPlugin.*/
	def projectConfigurations: Seq[Configuration] = Nil

	/** The [[Setting]]s to add in the scope of each project that activates this AutoPlugin. */
	def projectSettings: Seq[Setting[_]] = Nil

	/** The [[Setting]]s to add to the build scope for each project that activates this AutoPlugin.
	* The settings returned here are guaranteed to be added to a given build scope only once
	* regardless of how many projects for that build activate this AutoPlugin. */
	def buildSettings: Seq[Setting[_]] = Nil

	/** The [[Setting]]s to add to the global scope exactly once if any project activates this AutoPlugin. */
	def globalSettings: Seq[Setting[_]] = Nil

	// TODO?: def commands: Seq[Command]
}

/** An error that occurs when auto-plugins aren't configured properly.
* It translates the error from the underlying logic system to be targeted at end users. */
final class AutoPluginException private(val message: String, val origin: Option[LogicException]) extends RuntimeException(message)
{
	/** Prepends `p` to the error message derived from `origin`. */
	def withPrefix(p: String) = new AutoPluginException(p + message, origin)
}
object AutoPluginException
{
	def apply(msg: String): AutoPluginException = new AutoPluginException(msg, None)
	def apply(origin: LogicException): AutoPluginException = new AutoPluginException(Natures.translateMessage(origin), Some(origin))
}

package xsbt.boot

import Pre._
import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

object Initialize
{
	def create(file: File, promptCreate: String, enableQuick: Boolean, spec: List[AppProperty])
	{
		SimpleReader.readLine(promptCreate + " (y/N" + (if(enableQuick) "/s" else "") + ") ") match
		{
			case None => declined("")
			case Some(line) =>
				line.toLowerCase match
				{
					case "y" | "yes" => process(file, spec, _.create)
					case "s" => process(file, spec, _.quick)
					case "n" | "no" | "" => declined("")
					case x =>
						System.out.println("  '" + x + "' not understood.")
						create(file, promptCreate, enableQuick, spec)
				}
		}
	}
	def fill(file: File, spec: List[AppProperty]): Unit = process(file, spec, _.fill)
	def process(file: File, appProperties: List[AppProperty], select: AppProperty => Option[PropertyInit])
	{
		val properties = new Properties
		if(file.exists)
			Using(new FileInputStream(file))( properties.load )
		val uninitialized =
			for(property <- appProperties; init <- select(property) if properties.getProperty(property.name) == null) yield
				initialize(properties, property.name, init)
		if(!uninitialized.isEmpty)
		{
			file.getParentFile.mkdirs()
			Using(new FileOutputStream(file))( out => properties.save(out, "") )
		}
	}
	def initialize(properties: Properties, name: String, init: PropertyInit)
	{
		init match
		{
			case set: SetProperty => properties.setProperty(name, set.value)
			case prompt: PromptProperty =>
				def noValue = declined("No value provided for " + prompt.label)
				SimpleReader.readLine(prompt.label + prompt.default.toList.map(" [" + _ + "]").mkString + ": ") match
				{
					case None => noValue
					case Some(line) =>
						val value = if(isEmpty(line)) prompt.default.getOrElse(noValue) else line
						properties.setProperty(name, value)
				}
		}
	}
}

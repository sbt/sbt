package xsbt.boot

import java.io.{File, FileInputStream, FileOutputStream}
import java.util.Properties

object Initialize
{
	def create(file: File, promptCreate: String, enableQuick: Boolean, spec: Seq[AppProperty])
	{
		SimpleReader.readLine(promptCreate + " (y/N" + (if(enableQuick) "/s" else "") + ") ") match
		{
			case None => throw new BootException("")
			case Some(line) =>
				line.toLowerCase match
				{
					case "y" | "yes" => process(file, spec, _.create)
					case "n" | "no" | "" => throw new BootException("")
					case "s" => process(file, spec, _.quick)
				}
		}
	}
	def fill(file: File, spec: Seq[AppProperty]): Unit = process(file, spec, _.fill)
	def process(file: File, appProperties: Seq[AppProperty], select: AppProperty => Option[PropertyInit])
	{
		val properties = new Properties
		if(file.exists)
			Using(new FileInputStream(file))( properties.load )
		for(property <- appProperties; init <- select(property) if properties.getProperty(property.name) == null)
			initialize(properties, property.name, init)
		file.getParentFile.mkdirs()
		Using(new FileOutputStream(file))( out => properties.save(out, "") )
	}
	def initialize(properties: Properties, name: String, init: PropertyInit)
	{
		init match
		{
			case SetProperty(value) => properties.setProperty(name, value)
			case PromptProperty(label, default) =>
				def noValue = throw new BootException("No value provided for " + label)
				SimpleReader.readLine(label + default.toList.map(" [" + _ + "]").mkString + ": ") match
				{
					case None => noValue
					case Some(line) =>
						val value = if(line.isEmpty) default.getOrElse(noValue) else line
						properties.setProperty(name, value)
				}
		}
	}
}

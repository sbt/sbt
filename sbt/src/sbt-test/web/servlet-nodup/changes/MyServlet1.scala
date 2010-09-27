package test

import java.util.Collections

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

class MyServlet extends HttpServlet {

	val html = <HTML>
		<HEAD><TITLE>Hello, Scala!</TITLE></HEAD>
		<BODY>Hello, Scala! This is a servlet.</BODY>
	</HTML>

	override def doGet(req:HttpServletRequest, resp:HttpServletResponse) {
		val found = Collections.list(getClass.getClassLoader.getResources("test/MyServlet.class"))
		assert( found.size == 1, "Multiple instances of MyServlet.class on classpath")
		resp.setContentType("text/html")
		resp.getWriter().print(html.toString)
	}
	def check28(f: Int = 3) = f
}


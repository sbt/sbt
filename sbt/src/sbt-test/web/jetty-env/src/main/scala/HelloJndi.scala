import javax.servlet.http.{HttpServlet, HttpServletRequest => HSReq, HttpServletResponse => HSResp}
import javax.naming.{InitialContext => IC};

class HelloJndi extends HttpServlet {

  val key = "java:comp/env/testValue"
  val testValue = (new IC).lookup(key)

  override def doGet(req : HSReq, resp : HSResp) = 
    resp.getWriter().print("<HTML>" +
      "<HEAD><TITLE>Hello JNDI!</TITLE></HEAD>" +
      "<BODY>Hello JNDI, <br/>Value of " + key + ": " + testValue + "</BODY>" +
      "</HTML>")
}

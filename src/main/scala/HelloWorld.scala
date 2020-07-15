import zio.{ App, Task }

import scala.language.postfixOps
import scala.sys.process._

object HelloWorld extends App {

  val com = "ls -al" !!

  def run(args: List[String]) =
    myAppLogic.exitCode

  val myAppLogic =
    for {
      rows <- Task.effect("./blackbox.sh".lazyLines)
      _    = rows.foreach(input => println(input))
    } yield ()
}

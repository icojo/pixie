import java.io.{ File, OutputStream }
import java.net.{ InetSocketAddress, ServerSocket, Socket }
import java.nio.file.Files
import java.util.UUID

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{ Duration, Seconds, StreamingContext }
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.write
import uzhttp.Response
import uzhttp.server.Server
import zio.blocking._
import zio.console._
import zio.{ App, RIO, Task, ZManaged }

import scala.sys.process._
import scala.util.Try

object Application extends App {

  private val socketPort = 4444
  private val socketHost = "localhost"
  private val newLine    = "\n".getBytes()

  private val httpHost = "127.0.0.1"
  private val httpPort = 8080

  def run(args: List[String]) =
    createStreamingContext().use { ssc =>
      for {
        generatedRows    <- Task("./blackbox.sh".lineStream)
        resultsDirectory <- effectBlocking(Files.createTempDirectory("results").toString)
        _                <- putStrLn("Results dir=" + resultsDirectory)
        _                <- processStream(ssc, resultsDirectory, Seconds(10), socketHost, socketPort).fork
        _ <- createServerSocket(socketPort).use {
              case (_, socketOutputStream) =>
                for {
                  _ <- effectBlocking {
                        generatedRows.foreach { input =>
                          socketOutputStream.write(input.getBytes)
                          socketOutputStream.write(newLine)
                        }
                      }.fork
                  _ <- Server
                        .builder(new InetSocketAddress(httpHost, httpPort))
                        .handleSome {
                          case req if req.uri.getPath == "/" =>
                            readWindows(resultsDirectory).map(resp => Response.plain(write(resp)(DefaultFormats))).orDie
                        }
                        .serve
                        .useForever
                        .orDie
                } yield ()

            }
      } yield ()
    }.exitCode

  private final def createServerSocket(port: Int): ZManaged[Blocking, Throwable, (Socket, OutputStream)] =
    ZManaged.make(effectBlocking {
      val acceptor = new ServerSocket(port)
      val socket   = acceptor.accept()
      (socket, socket.getOutputStream)
    }) { case (s, _) => Task(s.close()).orDie }

  final case class Window(
    timestamp: Long,
    counts: List[String]
  )

  private final def readWindows(resultsDir: String): RIO[Blocking, List[Window]] = effectBlocking {
    new File(resultsDir)
      .listFiles()
      .filter(_.isDirectory)
      .map(windowResultsDir =>
        Window(
          timestamp = windowResultsDir.getName.split("-")(1).toLong,
          counts = readPartitionFileCounts(windowResultsDir)
        )
      )
      .toList
      .sortBy(_.timestamp)
  }

  import collection.JavaConverters._
  private final def readPartitionFileCounts(dir: File): List[String] =
    dir
      .listFiles()
      .filter(!_.isDirectory)
      .filter(_.getName.startsWith("part-"))
      .flatMap(value => Files.readAllLines(value.toPath).asScala)
      .toList

  final case class GeneratedRow(event_type: String, data: String, timestamp: Long)

  private final def createStreamingContext() =
    zio.Managed.make(Task {
      val conf = new SparkConf().setMaster("local[2]").setAppName("Pixie")
      val ssc  = new StreamingContext(conf, Seconds(1))
      ssc.checkpoint(Files.createTempDirectory("pixie_checkpoints_" + UUID.randomUUID().toString).toString)
      ssc
    })(sc => Task(sc.stop(true, true)).orDie)

  private final def processStream(
    ssc: StreamingContext,
    resultsDir: String,
    windowDuration: Duration,
    socketHost: String,
    socketPort: Int
  ) = Task {
    ssc
      .socketTextStream(socketHost, socketPort)
      .map { row =>
        implicit val formats: DefaultFormats.type = DefaultFormats
        val r                                     = Try(parse(row).extract[GeneratedRow]).getOrElse(null)
        r
      }
      .filter(_ != null)
      .window(windowDuration, windowDuration) //slide == window
      .map(_.event_type)
      .countByValue()
      .saveAsTextFiles(resultsDir + "/result")

    ssc.start()
    ssc.awaitTermination()
  }

}

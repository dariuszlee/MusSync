import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging

case class AuthCode(code : String)
 
class SessionActor extends Actor {
  val log = Logging(context.system, this)
  def receive = {
    case AuthCode(s) => log.info("Auth-Code: " + s)
    case _ => throw new Exception("Not valid")
  }
}

object SpotifyAuthentication extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val dbActor = system.actorOf(Props[SessionActor], "session")

  val serverSource = Http().bind(interface = "localhost", port = 8080)

  val requestHandler: HttpRequest => HttpResponse = {
    case HttpRequest(GET, uri, _, _, _) =>
      val query = uri.query()
      val code = query.get("code") match {
        case Some(s) => s
        case None => throw new Exception("Not valid")
      }
      dbActor ! AuthCode(code)
      HttpResponse(entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        "<html><body>Hello world!</body></html>"))
    case r: HttpRequest =>
      r.discardEntityBytes() // important to drain incoming HTTP Entity stream
      HttpResponse(404, entity = "Unknown resource!")
  }

  val bindingFuture: Future[Http.ServerBinding] =
    serverSource.to(Sink.foreach { connection =>
      println("Accepted new connection from " + connection.remoteAddress)

      connection handleWithSyncHandler requestHandler
      // this is equivalent to
      // connection handleWith { Flow[HttpRequest] map requestHandler }
    }).run()
}

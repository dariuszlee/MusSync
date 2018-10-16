import scala.io.StdIn
import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.persistence.PersistentActor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.server.Directives._

import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging

import com.softwaremill.sttp._

import play.api.libs.json.Json

object SessionActor {
  case class SessionData(token : String, refreshToken : String)
}

class SessionActor extends PersistentActor {
  import SessionActor._
  var session : SessionData = SessionData("", "");

  def persistenceId : String = "SessionPersistence"
  val log = Logging(context.system, this)
  def receiveCommand = {
    case s : SessionData => {
      log.info("New Session data: " + s)
      session = s
    }
  }

  def receiveRecover : PartialFunction[Any, Unit] = {
    case s : String => log.info(s)
  }
}
 
object TokenActor {
  case class AuthCode(code : String)
  def props(sessionActor : ActorRef): Props = Props(new TokenActor(sessionActor))
}

class TokenActor(sessionActor : ActorRef) extends Actor {
  import TokenActor._

  val client_id = "40b76927fb1a4841b2114bcda79e829a"
  val client_secret = "7eb1825fb44845b8bd463f9e883fa9a9"
  val callback = "http://localhost:8080/callback"

  val log = Logging(context.system, this)
  implicit val backend = HttpURLConnectionBackend()
  def receive = {
    case AuthCode(s) => 
    {
      log.info("Auth-Code: " + s)
      val tokenUri = uri"https://accounts.spotify.com/api/token"
      val map : Map[String, String] = Map("grant_type" -> "authorization_code", "code" -> s, "redirect_uri" -> callback, "client_id" -> client_id, "client_secret" -> client_secret)
      val request = sttp.body(map).post(tokenUri)
      val response = request.send()
      val data = response.body match {
        case Left(x) => Json.parse(x)
        case Right(x) => Json.parse(x).toString()
      }

    }
    case _ => throw new Exception("Not valid")
  }
}

object SpotifyAuthentication extends App {
  import TokenActor.AuthCode

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val dbActor = system.actorOf(Props[SessionActor], "storeToken")
  val tokenActor = system.actorOf(TokenActor.props(dbActor), "getToken")

  val route = {
    path("") {
      get {
        complete {
          "<html><body>Hello world!</body></html>"
        }
      }
    } ~
    path("callback") {
      get {
        parameters('code) { code =>
          tokenActor ! AuthCode(code)
          val authCodePrint = f"Auth code: $code"
          complete(authCodePrint)
        }
        
      }
    }
  }

  val bindingFuture = Http().bindAndHandle(route, interface = "localhost", 8080)
  println(s"Server online at http://localhost:8080. Press RETURN to stop...")
  StdIn.readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}

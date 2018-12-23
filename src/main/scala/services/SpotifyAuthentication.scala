import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.server.Directives._

import akka.persistence.PersistentActor
import akka.persistence.SnapshotOffer

import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink

import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import play.api.libs.json.Json
import play.api.libs.json.JsValue

object SessionActor {
  case class SessionData(token : String, refreshToken : String)
  case class RefreshData(token : String)
  case object SessionRequest

  def props : Props = Props(new SessionActor())
}

class SessionActor extends PersistentActor {
  import SessionActor._
  var session : SessionData = SessionData("", "");

  def persistenceId : String = "SessionPersistence"
  val log = Logging(context.system, this)
  def receiveCommand = {
    case s : SessionData => {
      session = s
      saveSnapshot(session)
    }
    case SessionRequest => sender ! session
    case RefreshData(token) => {
      session = SessionData(token, session.refreshToken)
      saveSnapshot(session)
    }
  }

  def receiveRecover : PartialFunction[Any, Unit] = {
    case s : SessionData => {
      log.info(s"Restore Token $s")
      session = s
    }
    case SnapshotOffer(_, s : SessionData) => {
      session = s
      log.info(s"Restore Token $s")
    }
  }
}
 
object TokenActor {
  case class AuthCode(code : String)
  case object RefreshToken
  case object GetToken
  def props: Props = Props(new TokenActor())
}

class TokenActor extends Actor {
  import TokenActor._
  import SessionActor._
  import context.dispatcher

  val client_id = "40b76927fb1a4841b2114bcda79e829a"
  val client_secret = "7eb1825fb44845b8bd463f9e883fa9a9"
  val callback = "http://localhost:8080/callback"

  val sessionActor = context.actorOf(SessionActor.props, "sessionActor")

  val log = Logging(context.system, this)
  implicit val timeout : Timeout = 1 second
  def receive = {
    case AuthCode(s) => 
    {
      // val tokenUri = uri"https://accounts.spotify.com/api/token"
      // val map : Map[String, String] = Map("grant_type" -> "authorization_code", "code" -> s, "redirect_uri" -> callback, "client_id" -> client_id, "client_secret" -> client_secret)
      // val request = sttp.body(map).post(tokenUri)
      // val response = request.send()
      // val data : JsValue = response.body match {
      //   case Left(x) => Json.parse(x)
      //   case Right(x) => Json.parse(x)
      // }
      // sessionActor ! SessionData((data \ "access_token").as[String], (data \ "refresh_token").as[String])
    }
    case RefreshToken =>
    {
      // val future = ask(sessionActor, SessionRequest)
      // val toSendBack = sender()
      // val data = future onComplete {
      //   case Success(s : SessionData) => {
      //     val tokenUri = uri"https://accounts.spotify.com/api/token"
      //     val map : Map[String, String] = Map("grant_type" -> "refresh_token", "refresh_token" -> s.refreshToken, "redirect_uri" -> callback, "client_id" -> client_id, "client_secret" -> client_secret)
      //     val request = sttp.body(map).post(tokenUri)
      //     val response = request.send()
      //     val data : String = response.body match {
      //       case Right(x) => (Json.parse(x) \ "access_token").as[String] 
      //       case _ => throw new Exception("Refresh failed")
      //     }
      //     println("Refreshed Token: " + data)
      //     sessionActor ! RefreshData(data)
      //     toSendBack ! AuthCode(data)
      //   }
      //   case s : Any => {
      //     println(s)
      //   }
      // }
    }
    case GetToken => {

    }
    case _ => throw new Exception("Not valid")
  }
}

object SpotifyAuthentication extends App {
  import TokenActor.AuthCode
  import TokenActor.RefreshToken
  import SessionActor.SessionRequest
  import SessionActor.SessionData

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  implicit val timeout : Timeout = 5 second

  val dbActor = system.actorOf(Props[SessionActor], "storeToken")
  val tokenActor = system.actorOf(TokenActor.props, "getToken")

  val route = {
    path("session") {
      get {
        onComplete(ask(dbActor, SessionRequest)) {
          case Success(value : SessionData) => { 
            println(value)
            complete(Json.toJson(Map("token" -> value.token, "refresh" -> value.refreshToken)).toString())
          }
          case Success(value) => { 
            complete("Successfully but with no value")
          }
          case Failure(ex)    => {
            println(ex)
            complete("Failure")
          }
        }
      }
    } ~
    path("refresh") {
      get {
        onComplete(ask(tokenActor, RefreshToken)) {
          case Success(s : AuthCode) => {
            complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, 
              Json.toJson(Map("token" -> s.code)).toString())))
          }
          case _ => {
            complete(StatusCode.int2StatusCode(500))
          }
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
  readLine() // let it run until user presses return
  bindingFuture
    .flatMap(_.unbind()) // trigger unbinding from the port
    .onComplete(_ => system.terminate()) // and shutdown when done
}

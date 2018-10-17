import scala.io.StdIn
import scala.concurrent.Future
import scala.util.Success
import scala.util.Failure

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

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._


import com.softwaremill.sttp._

import play.api.libs.json.Json
import play.api.libs.json.JsValue

object SessionActor {
  case class SessionData(token : String, refreshToken : String)
  case object SessionRequest
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
    case SessionRequest => sender ! session
  }

  def receiveRecover : PartialFunction[Any, Unit] = {
    case s : String => log.info(s)
  }
}
 
object TokenActor {
  case class AuthCode(code : String)
  case object RefreshToken
  def props(sessionActor : ActorRef): Props = Props(new TokenActor(sessionActor))
}

class TokenActor(sessionActor : ActorRef) extends Actor {
  import TokenActor._
  import SessionActor._
  import context.dispatcher

  val client_id = "40b76927fb1a4841b2114bcda79e829a"
  val client_secret = "7eb1825fb44845b8bd463f9e883fa9a9"
  val callback = "http://localhost:8080/callback"

  val log = Logging(context.system, this)
  implicit val backend = HttpURLConnectionBackend()
  implicit val timeout : Timeout = 1 second
  def receive = {
    case AuthCode(s) => 
    {
      val tokenUri = uri"https://accounts.spotify.com/api/token"
      val map : Map[String, String] = Map("grant_type" -> "authorization_code", "code" -> s, "redirect_uri" -> callback, "client_id" -> client_id, "client_secret" -> client_secret)
      val request = sttp.body(map).post(tokenUri)
      val response = request.send()
      val data : JsValue = response.body match {
        case Left(x) => Json.parse(x)
        case Right(x) => Json.parse(x)
      }
      sessionActor ! SessionData((data \ "access_token").as[String], (data \ "refresh_token").as[String])
    }
    case RefreshToken =>
    {

      val future = ask(sessionActor, SessionRequest)
      future onComplete {
        case Success(s : SessionData) => {
          val tokenUri = uri"https://accounts.spotify.com/api/token"
          val map : Map[String, String] = Map("grant_type" -> "refresh_token", "refresh_token" -> s.refreshToken, "redirect_uri" -> callback, "client_id" -> client_id, "client_secret" -> client_secret)
          val request = sttp.body(map).post(tokenUri)
          val response = request.send()
          val data : JsValue = response.body match {
            case Left(x) => Json.parse(x)
            case Right(x) => Json.parse(x)
          }
          println(data)
        }
        case Success(s) => {
          println(s)
        }
        case Failure(s) => {
          println(s)
        }
      }
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

  implicit val timeout : Timeout = 1 second

  val dbActor = system.actorOf(Props[SessionActor], "storeToken")
  val tokenActor = system.actorOf(TokenActor.props(dbActor), "getToken")

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
        tokenActor ! RefreshToken
        complete("Refreshed")
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

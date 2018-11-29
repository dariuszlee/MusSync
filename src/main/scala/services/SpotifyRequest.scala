import scala.util.Try
import scala.util.Success
import scala.util.Failure

import org.apache.logging.log4j.scala.Logging
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.Level

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import akka.actor.Props
import akka.actor.Actor
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem

import play.api.libs.json.Json
import play.api.libs.json.JsValue
import com.softwaremill.sttp._

object SpotifyRequestActor {
  case class SpotifyRequest(uri : String)
  case class SpotifyResponse(res : JsValue)

  val logger = LogManager.getLogger()
  implicit val backend = HttpURLConnectionBackend()
  val auth_api = "http://localhost:8080"
  val token_path = "/refresh"
  val token_uri = auth_api + token_path
  
  def refresh_token() : String = 
    Try(sttp.get(uri"$token_uri").send().body).map(x => x match {
      case Right(x) => Try((Json.parse(x) \ "token").as[String]).map(s => s)
      case _ => {
        logger.error("HTTP Response code is not valid")
        throw new Exception("HTTP Response code is not valid")
      }
    }).flatten match {
      case Success(s) => s
      case Failure(ex) => {
        logger.error(s"token_uri not accessible: $token_uri")
        logger.error("Full Exception: " + ex.toString())
        return ""
      }
    }
}

class SpotifyRequestActor extends Actor with Logging {
  import SpotifyRequestActor._

  var token : String = refresh_token()
  def receive = {
    case SpotifyRequest(uri) => {
      val formattedUri = uri"$uri"
      val ret = Try(sttp.auth.bearer(token).get(formattedUri).send()).flatMap({
        case Response(_, 401, _, _, _) => {
          token = refresh_token()
          Try(sttp.auth.bearer(token).get(formattedUri).send()).flatMap(x => x match {
            case Response(Right(x), _, _, _, _) => Try(x)
            case _ => throw new Exception("After retry, we still fail.")
          })
        }
        case Response(Right(x), _, _, _, _) => Try(x)
        case _ => throw new Exception("Unhandled Condition in request")
      }) match {
        case Success(s) => sender ! SpotifyResponse(Json.parse(s))
        case Failure(ex) => 
      }
    }
  }
}

object SpotifyRequestTest extends App {
  val testUri = "https://api.spotify.com/v1/users/fishehh/playlists"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  implicit val timeout : Timeout = 5 second

  val reqActor = system.actorOf(Props[SpotifyRequestActor], "spotify_requester")
  ask(reqActor, SpotifyRequestActor.SpotifyRequest(testUri)) onComplete({
    case Success(x) => println(x)
    case Failure(x) => println(x)
  })

  readLine()
  system.terminate()
}

import scala.util.Success
import scala.util.Failure

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import akka.actor.Props
import akka.actor.Actor
import akka.stream.ActorMaterializer
import akka.actor.ActorSystem

import play.api.libs.json.Json
import play.api.libs.json.JsValue

import akka.event.LoggingAdapter

import akka.http.scaladsl.Http
import akka.util.ByteString
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

object SpotifyRequestActor {
  case class SpotifyRequest(uri : String)
  case class SpotifyResponse(res : JsValue)

  val auth_api = "http://localhost:8080"
  val token_path = "/refresh"
  val token_uri = auth_api + token_path

  def props(materializer: ActorMaterializer) : Props = Props(new SpotifyRequestActor()(materializer))
  
  // def refresh_token(log : LoggingAdapter) : String = 
  //   Try(sttp.get(uri"$token_uri").send().body).map(x => x match {
  //     case Right(x) => Try((Json.parse(x) \ "token").as[String]).map(s => s)
  //     case _ => {
  //       log.error("HTTP Response code is not valid")
  //       throw new Exception("HTTP Response code is not valid")
  //     }
  //   }).flatten match {
  //     case Success(s) => s
  //     case Failure(ex) => {
  //       log.error(s"token_uri not accessible: $token_uri")
  //       log.error("Full Exception: " + ex.toString())
  //       return ""
  //     }
  //   }
  def refresh_token(log : LoggingAdapter) : String = {
    return ""
  }
}

class SpotifyRequestActor()(implicit mat: ActorMaterializer) extends Actor with akka.actor.ActorLogging {
  import SpotifyRequestActor._
  import context._

  var token : String = refresh_token(log)
  def receive = {
    case SpotifyRequest(uri) => {
      AkkaHttpUtils.request_oauth(uri, token).onComplete({
        case Success(res: String) => {
          sender() ! SpotifyResponse(Json.parse(res))
        }
        case Failure(_)   => sys.error("something wrong")
      })
    }
  }
}

object SpotifyRequestTest extends App {
  import SpotifyAuthentication._
  val testUri = "https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  implicit val timeout : Timeout = 5 second

  val authActor = system.actorOf(TokenActor.props, "authActor")
  val reqActor = system.actorOf(SpotifyRequestActor.props(materializer), "spotify_requester")
  // ask(reqActor, SpotifyRequestActor.SpotifyRequest(testUri)) onComplete({
  //   case Success(x) => println(x)
  //   case Failure(x) => println(x)
  // })
  reqActor ! SpotifyRequestActor.SpotifyRequest("https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1")

  readLine()
  system.terminate()
}

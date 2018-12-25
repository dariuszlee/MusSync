import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import scala.util.Success
import scala.util.Failure

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import akka.actor.Props
import akka.actor.Actor
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import akka.actor.ActorSystem

import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.JsDefined

import akka.event.LoggingAdapter

import akka.http.scaladsl.Http
import akka.util.ByteString
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

object SpotifyRequestActor {
  case class SpotifyRequest(uri : String)
  case class SpotifyResponse(res : JsValue)

  val auth_api = "http://localhost:8080"
  val token_path = "/session"
  val token_uri = auth_api + token_path

  def props(materializer: ActorMaterializer) : Props = Props(new SpotifyRequestActor()(materializer))
  
  def refresh_token(log : LoggingAdapter)(implicit mat: Materializer, context: ExecutionContext, system: ActorSystem) : Future[String] = {
    AkkaHttpUtils.request_json(token_uri).map(x => {
        (x \ "token").as[String]
      }
    )
  }
}

class SpotifyRequestActor()(implicit mat: ActorMaterializer) extends Actor with akka.actor.ActorLogging {
  import SpotifyRequestActor._
  import TokenActor._
  import context._

  implicit val timeout : akka.util.Timeout = 5 second
  val authActor = context.actorSelection("/user/auth_actor")
  def receive = {
    case SpotifyRequest(uri) => {
      val responseActor = sender()
      ask(authActor, GetToken).onComplete({
        case Success(SessionToken(token)) => {
          AkkaHttpUtils.request_oauth_raw(uri, token).onComplete({
            case Success((StatusCodes.Unauthorized, _)) => {
              authActor ! RefreshToken
              self ! SpotifyRequest(uri)
            }
            case Success((StatusCodes.Success(_), data: Future[String])) => {
              data.onComplete({
                case Success(dataString) => responseActor ! SpotifyResponse(Json.parse(dataString))
              })
            }
            case Failure(_) => sys.error("something wrong")
          })
        }
      })
    }
  }
}

object SpotifyRequestTest extends App {
  import TokenActor._
  val testUri = "https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout : akka.util.Timeout = 5 second

  val authActor = system.actorOf(TokenActor.props, "auth_actor")
  val reqActor = system.actorOf(SpotifyRequestActor.props(materializer), "spotify_requester")

  ask(reqActor, SpotifyRequestActor.SpotifyRequest(testUri)) onComplete({
    case Success(x) => println(x)
    case Failure(x) => println(x)
  })
  // reqActor ! SpotifyRequestActor.SpotifyRequest("https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1")

  readLine()
  system.terminate()
}

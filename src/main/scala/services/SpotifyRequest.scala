import scala.util.Try
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
import com.softwaremill.sttp._

import akka.event.LoggingAdapter

import akka.http.scaladsl.Http
import akka.util.ByteString
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

object SpotifyRequestActor {
  case class SpotifyRequest(uri : String)
  case class SpotifyRequestAkka(uri : String)
  case class SpotifyResponse(res : JsValue)

  implicit val backend = HttpURLConnectionBackend()
  val auth_api = "http://localhost:8080"
  val token_path = "/refresh"
  val token_uri = auth_api + token_path

  def props(materializer: ActorMaterializer) : Props = Props(new SpotifyRequestActor()(materializer))
  
  def refresh_token(log : LoggingAdapter) : String = 
    Try(sttp.get(uri"$token_uri").send().body).map(x => x match {
      case Right(x) => Try((Json.parse(x) \ "token").as[String]).map(s => s)
      case _ => {
        log.error("HTTP Response code is not valid")
        throw new Exception("HTTP Response code is not valid")
      }
    }).flatten match {
      case Success(s) => s
      case Failure(ex) => {
        log.error(s"token_uri not accessible: $token_uri")
        log.error("Full Exception: " + ex.toString())
        return ""
      }
    }
}

class SpotifyRequestActor()(implicit mat: ActorMaterializer) extends Actor with akka.actor.ActorLogging {
  import SpotifyRequestActor._
  import context._

  var token : String = refresh_token(log)
  def receive = {
    case SpotifyRequest(uri) => {
      val formattedUri = uri"$uri"
      val ret = Try(sttp.auth.bearer(token).get(formattedUri).send()).flatMap({
        case Response(_, 401, _, _, _) => {
          token = refresh_token(log)
          Try(sttp.auth.bearer(token).get(formattedUri).send()).flatMap(x => x match {
            case Response(Right(x), _, _, _, _) => Try(x)
            case _ => throw new Exception("After retry, we still fail.")
          })
        }
        case Response(Right(x), _, _, _, _) => Try(x)
        case _ => throw new Exception("Unhandled Condition in request")
      }) match {
        case Success(s) => sender ! SpotifyResponse(Json.parse(s))
        case Failure(ex) => log.error("Request Failed with: {} on uri: {}", ex, uri)
      }
    }
    case SpotifyRequestAkka(uri) => {
      val request = HttpRequest(uri = uri).addCredentials(new OAuth2BearerToken(token))
      val responseFuture = Http().singleRequest(request)
      responseFuture onComplete {
        case Success(res) => res match {
          case HttpResponse(akka.http.scaladsl.model.StatusCodes.OK, _, entity, _) => {
            entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(body => body.utf8String) onComplete {
              case Success(res) => log.info("{}", res)
            }
          }
        }
        case Failure(_)   => sys.error("something wrong")
      }
    }
  }
}

object SpotifyRequestTest extends App {
  // val testUri = "https://api.spotify.com/v1/users/fishehh/playlists"
  val testUri = "https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  implicit val timeout : Timeout = 5 second

  val reqActor = system.actorOf(SpotifyRequestActor.props(materializer), "spotify_requester")
  // ask(reqActor, SpotifyRequestActor.SpotifyRequest(testUri)) onComplete({
  //   case Success(x) => println(x)
  //   case Failure(x) => println(x)
  // })
  reqActor ! SpotifyRequestActor.SpotifyRequestAkka("https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1")

  readLine()
  system.terminate()
}

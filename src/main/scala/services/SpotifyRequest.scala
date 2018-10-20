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

  implicit val backend = HttpURLConnectionBackend()
  val auth_api = "http://localhost:8080"
  val token_uri = "/refresh"
  
  def refresh_token() : String = {
    val tokenUriString = auth_api + token_uri
    val tokenResponse = sttp.get(uri"$tokenUriString").send()
    var token : String = tokenResponse.body match {
      case Left(x) => {
        throw new Exception("Please start local auth server")
      }
      case Right(x) => {
        (Json.parse(x) \ "token").as[String]
      }
    }

    return token
  }
}

class SpotifyRequestActor extends Actor {
  import SpotifyRequestActor._

  var token : String = refresh_token()
  def receive = {
    case SpotifyRequest(uri) => {
      val formattedUri = uri"$uri"
      sttp.auth.bearer(token).get(formattedUri).send() match {
        case Response(_, 401, _, _, _) => {
        }
        case Response(Right(x), _, _, _, _) => {
        }
        case _ => throw new Exception("Unhandled Condition in request")
      }
      // sender() ! SpotifyResponse(Json.parse(playlistResponse))
    }
  }
}

object SpotifyRequestTest extends App {
  val testUri = "https://api.spotify.com/v1/users/fishehh/playlists"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val reqActor = system.actorOf(Props[SpotifyRequestActor], "spotify_requester")
  reqActor ! SpotifyRequestActor.SpotifyRequest(testUri)

  readLine()
  system.terminate()
}

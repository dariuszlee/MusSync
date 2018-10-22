import scala.concurrent.duration.MINUTES
import scala.concurrent.duration.Duration
import scala.concurrent.duration.TimeUnit
import scala.concurrent.Await

import scala.util.Try
import scala.util.Success
import scala.util.Failure

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import akka.stream.ActorMaterializer

import akka.event.Logging

import com.softwaremill.sttp._
import play.api.libs.json.Json

import services.SpotifyUtility

object UserPlaylistActor {
  implicit val backend = HttpURLConnectionBackend()

  val base_api_url = "https://api.spotify.com/v1"
  def user_playlist_uri(id : String) : String  = return s"/users/$id/playlists"
  val playlists_api_url = ""

  val auth_api = "http://localhost:8080"
  val token_uri = "/session"

  case class UserId(id : String)

  def get_playlists(id : String) : String = {
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

    val playlistUri = base_api_url + user_playlist_uri(id)
    val playlistsResponse = sttp.auth.bearer(token).get(uri"$playlistUri").send()
    val data = playlistsResponse match {
      case Response(Left(x), 401, _, _, _) => 
      case Response(Right(x), _, _, _, _) => Json.parse(x)
      case Response(Left(x), _, _, _, _) => throw new Exception("Unknown error: " + Json.parse(x).toString())
    }
    return data.toString()
  }
}

class UserPlaylistActor(reqActor : ActorRef) extends Actor {
  import UserPlaylistActor.UserId
  import context.dispatcher

  val log = Logging(context.system, this)
  implicit val timeout : Timeout = 5 second

  def receive = {
    case UserId(id) => {
      val playlistUri = UserPlaylistActor.base_api_url + UserPlaylistActor.user_playlist_uri(id)
      ask(reqActor, SpotifyRequestActor.SpotifyRequest(playlistUri)) onComplete({
        case Success(x) => println(x)
        case Failure(x) => println(x)
      })
    }
  }
}

object SpotifyCrawler extends App {
  import UserPlaylistActor.UserId

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val requestActor = system.actorOf(Props[SpotifyRequestActor], "reqActor")

  val userPlaylistActor = system.actorOf(Props(new UserPlaylistActor(requestActor)), "upActor")
  userPlaylistActor ! UserId("fishehh")

  readLine()
  system.terminate()
}

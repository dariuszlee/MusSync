import SpotifyRequestActor._

import scala.util.Try
import scala.util.Success
import scala.util.Failure

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import play.api.libs.json.JsValue
import play.api.libs.json.JsDefined

object SpotifyFollow {
  case class GetFollows(uri : Option[String])
  val getFollowersUri : String = "https://api.spotify.com/v1/me/following?type=artist"
}

class SpotifyFollow(reqActor : ActorRef) extends Actor {
  import SpotifyFollow._
  import context.dispatcher
  implicit val timeout : Timeout = 3 seconds

  val spotifyFollowActors = context.actorSelection("akka://default/user/spotifyFollow")

  def receive = {
    case GetFollows(uri) => {
      val requestUri = uri getOrElse getFollowersUri
      ask(reqActor, SpotifyRequestActor.SpotifyRequest(requestUri)) onComplete({
        case Success(SpotifyResponse(y)) => {
          println(y \ "artists" \ "next")
          
          Try((y \ "artists" \ "next")) getOrElse None match {
            case Some(JsDefined(null)) => println("Finished")
            case Some(JsDefined(x : String)) => spotifyFollowActors ! GetFollows(Some(x))
            case None => println("Operation failed")
          }
          
        }
        case Success(x) => println("Unknown response type: " + x)
        case Failure(x) => println(x)
      })
    }
  }
}

object SpotifyReleaseManager extends App {
  import SpotifyFollow._
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val requestActor = system.actorOf(Props[SpotifyRequestActor], "reqActor")

  val userPlaylistActor = system.actorOf(Props(new SpotifyFollow(requestActor)), "spotifyFollow")
  userPlaylistActor ! GetFollows(None)

  readLine()
  system.terminate()
}

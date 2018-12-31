import SpotifyRequestActor._
import akka.pattern.pipe

import scala.util.Success
import scala.util.Failure

import akka.routing.SmallestMailboxPool

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsString
import play.api.libs.json.JsDefined

object SpotifyFollowRecursive {
  def props : Props = Props(new SpotifyFollowRecursive())
  val getFollowersUri : String = "https://api.spotify.com/v1/me/following?type=artist&limit=20"

  case class GetFollowers(uri : String)
}

class SpotifyFollowRecursive extends Actor with akka.actor.ActorLogging {
  import SpotifyFollowRecursive._
  val reqActor = context.actorSelection("/user/request-actors")

  import context.dispatcher
  implicit val timeout : Timeout = 3 seconds

  def receive = {
    case GetFollowers(uri) => {
      ask(reqActor, SpotifyRequestActor.SpotifyRequest(uri)).map(x => {
        x
      }).pipeTo(self)
    }
    case SpotifyResponse(y) => {
      (y \ "artists" \ "next").as[JsValue] match {
        case JsString(nextUri) => {
          val actorId = (y \ "artists" \  "cursors" \ "after").as[String]
          val nextActor = context.actorOf(props, s"get-next-$actorId")
          nextActor ! GetFollowers(nextUri)
        }
        case JsNull => log.info("Finished: There is no next value.")
      }
      (y \ "artists" \ "items").as[JsValue] match {
        case JsArray(artists) => {
          val artistActor = context.actorOf(SpotifyArtist.props, "artist-actor")
          artists.foreach(artist => {
            artistActor ! SpotifyArtist.GetLatest((artist \ "id").as[String])
          })
        }
      }
    }
  }
}

object SpotifyReleaseManager extends App {
  import SpotifyFollowRecursive._
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val authActor = system.actorOf(TokenActor.props, "auth_actor")
  // val requestActors = system.actorOf(SmallestMailboxPool(5).props(SpotifyRequestActor.props(materializer)), "request-actors")
  val requestActors = system.actorOf(SpotifyRequestActor.props, "request-actors")

  val userFollows = system.actorOf(props, "spotifyFollow")
  userFollows ! GetFollowers(SpotifyFollowRecursive.getFollowersUri)

  readLine()
  system.terminate()
}

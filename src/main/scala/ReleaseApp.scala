import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._

import scala.collection.mutable.HashMap

object ReleaseActor {
  def props : Props = Props(new ReleaseActor())
  def initial_url : String = "https://api.spotify.com/v1/me/following?type=artist&limit=20"

  case object StartJob
  case object CheckJob
  case class FinishedUrl(url: String)
  case class AddUrl(url: String)
  class EndJob extends Exception
}

class ReleaseActor extends Actor with akka.actor.ActorLogging {
  import ReleaseActor._
  import ArtistActor._
  import context._

  implicit var mat = ActorMaterializer()(context.system)
  var completed : HashMap[String, Boolean] = new HashMap[String, Boolean]()
  var request_actor = context.actorOf(SpotifyRequestActor.props, "req_actor_root")

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case ex: EndJob => {
        log.error("Handling error: {}", ex)
        Resume
      }
    }

  import java.security.MessageDigest
  def hash(to_hash : String) = {
    MessageDigest.getInstance("MD5").digest(to_hash.getBytes)
  }

  override def receive = {
    case StartJob => {
      self ! AddUrl(initial_url)
    }
    case AddUrl(uri: String) => {
      val uri_hash = "artist_act_" + BigInt(hash(uri))
      log.info("URI {}", uri_hash)
      val get_artists_actor = context.actorOf(ArtistActor.props, uri_hash)
      completed.put(uri, false)
      get_artists_actor ! HandleArtistUrl(uri)
    }
    case CheckJob => {
      log.info("Checking if job is completed. {}", completed)
    }
    case FinishedUrl(url) => {
      completed.put(url, true)
    }
  }
}

object ReleaseApp extends App {
  implicit val context = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.dispatcher

  val spotify_req = context.actorOf(SpotifyRequestActor.props, "req_actor")
  val release_app = context.actorOf(ReleaseActor.props, "release-actor")
  release_app ! ReleaseActor.StartJob

  readLine()
  context.terminate()
}

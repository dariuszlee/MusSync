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

  var completed : HashMap[String, Boolean] = new HashMap[String, Boolean]()

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case ex: EndJob => {
        log.error("Handling error: {}", ex)
        Resume
      }
    }

  override def receive = {
    case StartJob => {
      val get_artists_actor = context.actorOf(ArtistActor.props, "artist_actor")
      completed.put(initial_url, false)
      get_artists_actor ! HandleArtistUrl(initial_url)
    }
    case AddUrl(uri: String) => {
      completed.add(uri, false)
    }
    case CheckJob => {
      log.info("Checking if job is completed. {}", completed)
    }
    case FinishedUrl(url, next) => {
      completed.put(url, true)
      next match {
        case Some(x) => log.info("Starting next")
        case None => self ! CheckJob
      }
    }
  }
}

object ReleaseApp extends App {
  implicit val context = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.dispatcher

  val release_app = context.actorOf(ReleaseActor.props, "release-actor")
  release_app ! ReleaseActor.StartJob

  readLine()
  context.terminate()
}

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props

object SpotifyArtist {
  def props : Props = Props(new SpotifyArtist())

  case class GetLatest(artistId : String)
}

class SpotifyArtist extends Actor with ActorLogging {
  import SpotifyArtist._

  def receive = {
    case GetLatest(id) => {
      log.debug("Artist Id: {}", id)
    }
  }
}


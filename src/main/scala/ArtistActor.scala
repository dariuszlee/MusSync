import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

object ArtistActor {
  def props : Props = Props(new ArtistActor())

  case class HandleArtistUrl(url: String)
  case object CompleteArtistUrl
}

class ArtistActor extends Actor with akka.actor.ActorLogging {
  import ArtistActor._
  import ReleaseActor.FinishedUrl

  var to_respond_to : Option[ActorRef] = None
  var completed = false
  var internal_url : Option[String] = None

  override def receive = {
    case HandleArtistUrl(url) => {
      internal_url = Some(url)
      to_respond_to = Some(sender())
      Thread.sleep(1000)
      self ! CompleteArtistUrl
    }
    case CompleteArtistUrl => {
      completed = true
      (to_respond_to.get) ! FinishedUrl(internal_url.get, None)   
    }
  }
}

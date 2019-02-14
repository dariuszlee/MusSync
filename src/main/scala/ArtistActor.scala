import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import play.api.libs.json.JsString
import play.api.libs.json.JsNull
import play.api.libs.json.JsDefined

import scala.collection.mutable.Queue

object ArtistActor {
  def props : Props = Props(new ArtistActor())

  case class HandleArtistUrl(url: String)
  case object CompleteArtistUrl
}

class ArtistActor extends Actor with akka.actor.ActorLogging {
  import ArtistActor._
  import ReleaseActor._
  import SpotifyRequestActor._

  var to_respond_to : Option[ActorRef] = None
  var completed = false
  var internal_url : Option[String] = None
  var failed_requests = new Queue[String]()

  val req_actor = context.actorSelection("/user/req_actor")

  override def receive = {
    case HandleArtistUrl(url) => {
      internal_url = Some(url)
      to_respond_to = Some(sender())
      req_actor ! SpotifyRequest(url)
    }
    case SpotifyResponse(x) => {
      x \ "artists" \ "next" match {
        case JsDefined(JsString(x)) => {
          to_respond_to.get ! AddUrl(x)
        }
        case JsDefined(JsNull) => println("Finished....")
        case _ => log.error("Invalid input")
      }
      // to_respond_to.get ! AddUrl(next.as[String])
    }
    case CompleteArtistUrl => {
      completed = true
      (to_respond_to.get) ! FinishedUrl(internal_url.get)   
    }
  }
}

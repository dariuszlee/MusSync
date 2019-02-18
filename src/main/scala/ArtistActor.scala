import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import play.api.libs.json.JsNull
import play.api.libs.json.JsDefined

import scala.collection.mutable.Queue
import scala.collection.mutable.HashMap

import SpotifyRequestActor._

object ArtistActor {
  def props : Props = Props(new ArtistActor())

  case class HandleJobStart(url: String)
  case class HandleArtistUrl(url: String)
  case class HandleJobEnd(x : SpotifyResponse)
  case object CompleteArtistUrl
}

class ArtistActor extends Actor with akka.actor.ActorLogging {
  import ArtistActor._
  import ReleaseActor._

  var to_respond_to : Option[ActorRef] = None
  var completed = false
  var internal_url : Option[String] = None
  var failed_requests = new Queue[String]()

  var complete_map : HashMap[String, Boolean] = new HashMap[String, Boolean]()

  val req_actor = context.actorSelection("/user/req_actor")

  override def receive = {
    case HandleJobStart(url) => {
      internal_url = Some(url)
      to_respond_to = Some(sender())
      req_actor ! SpotifyRequest(url, HandleJobEnd)
    }
    case HandleJobEnd(SpotifyResponse(x)) => {
      x \ "artists" \ "next" match {
        case JsDefined(JsString(x)) => {
          to_respond_to.get ! AddUrl(x)
        }
        case JsDefined(JsNull) => println("Finished....")
        case _ => log.error("Invalid input")
      }
      for (artist <- (x \ "artists" \ "items").as[Seq[JsObject]]) {
        val spot_url = (artist \ "external_urls" \ "spotify").as[String]
        // self ! Handl
      }
      // to_respond_to.get ! AddUrl(next.as[String])
    }
    case CompleteArtistUrl => {
      completed = true
      (to_respond_to.get) ! FinishedUrl(internal_url.get)   
    }
  }
}

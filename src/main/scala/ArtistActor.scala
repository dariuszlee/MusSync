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
  case class HandleJobEnd(x : SpotifyResponse)
  case class HandleIndividualArtist(url: String)
  case class CompletedIndividualArtist(x : SpotifyResponse)
  case object CheckStatus
}

class ArtistActor extends Actor with akka.actor.ActorLogging {
  import ArtistActor._
  import ReleaseActor._

  var to_respond_to : Option[ActorRef] = None
  var internal_url : Option[String] = None

  var completed_initial = false

  var complete_map : Set[String] = Set[String]()

  val req_actor = context.actorSelection("/user/req_actor")

  override def receive = {
    case HandleJobStart(url) => {
      internal_url = Some(url)
      to_respond_to = Some(sender())
      req_actor ! SpotifyRequest(url, HandleJobEnd)
    }
    case HandleJobEnd(SpotifyResponse(x, _)) => {
      x \ "artists" \ "next" match {
        case JsDefined(JsString(x)) => {
          to_respond_to.get ! AddUrl(x)
        }
        case JsDefined(JsNull) => println("Finished....")
        case _ => log.error("Invalid input")
      }
      for (artist <- (x \ "artists" \ "items").as[Seq[JsObject]]) {
        val spot_url = (artist \ "href").as[String]
        self ! HandleIndividualArtist(spot_url)
      }
      completed_initial = true
    }
    case HandleIndividualArtist(url) => {
      val album_url = url + "/albums"
      complete_map = complete_map + album_url
      req_actor ! SpotifyRequest(album_url, CompletedIndividualArtist)    
    }
    case CompletedIndividualArtist(SpotifyResponse(x, SpotifyRequest(url, _))) => {
      log.info("Completing url {}", url)
      complete_map = complete_map - url
    }
    case CheckStatus => {
      if (completed_initial){
        if(complete_map.isEmpty){
          log.info("COMPLETED {}", internal_url.get)
          (to_respond_to.get) ! FinishedUrl(internal_url.get)   
        }
        else {
          complete_map.foreach(x => HandleIndividualArtist(x))
        }
      }
    }
  }
}

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import play.api.libs.json.JsString
import play.api.libs.json.JsValue
import play.api.libs.json.JsNumber
import play.api.libs.json.JsObject
import play.api.libs.json.JsNull
import play.api.libs.json.JsDefined

import scala.collection.mutable.Queue
import scala.collection.mutable.HashMap

import SpotifyRequestActor._
import SpotifyDbActor._

object ArtistActor {
  def props(url : String, respond_to: ActorRef, is_debug: Boolean) : Props = Props(new ArtistActor(url, respond_to, is_debug))

  case class HandleJobStart(url: String)
  case class HandleJobStartInternal(url: String)
  case class HandleJobEnd(x : SpotifyResponse)
  case class HandleIndividualArtist(url: String)
  case class CompletedIndividualArtist(x : SpotifyResponse)
  case object CheckStatus
  case class CheckArtistResponse(check_res: Boolean)

  def get_latest_artist(artists : JsValue) : Option[String] = {
    (artists \ "items").as[List[JsValue]] match {
      case x :: _ => Some((x \ "id").as[String])
      case _ => None
    }
  }
}

class ArtistActor(work_url : String, respond_to: ActorRef, dump_mode: Boolean) extends Actor with akka.actor.ActorLogging {
  import ArtistActor._
  import ReleaseActor._

  var completed_initial = false

  var working_map : Set[String] = Set[String]()
  var completed_map : Set[String] = Set[String]()

  val req_actor = context.actorSelection("/user/req_actor")
  val db_actor = context.actorSelection("/user/db-actor")

  var total_individual_requests = 0
  var internal_total_artists : BigDecimal = 0

  var checking_map : Map[String, String] = Map[String,String]()

  override def receive = {
    case HandleJobStart(url) => {
      self ! HandleJobStartInternal(url)
    }
    case HandleJobStartInternal(url) => {
      req_actor ! SpotifyRequest(url, HandleJobEnd)
    }
    case HandleJobEnd(SpotifyResponse(x, _)) => {
      x \ "artists" \ "total" match {
        case JsDefined(JsNumber(total_artists)) => {
          log.info("TOTAL ARTISTS: {}", total_artists)
          internal_total_artists = total_artists
        }
        case _ => log.error("No total value")
      }
      x \ "artists" \ "next" match {
        case JsDefined(JsString(x)) => {
          respond_to ! AddUrl(x)
        }
        case JsDefined(JsNull) => println("Finished....")
        case _ => log.error("Invalid input")
      }
      for (artist <- (x \ "artists" \ "items").as[Seq[JsObject]]) {
        val artist_id = (artist \ "id").as[String]
        val url = s"https://api.spotify.com/v1/artists/$artist_id/albums"
        self ! HandleIndividualArtist(url)
      }
      completed_initial = true
    }
    case HandleIndividualArtist(url) => {
      working_map = working_map + url 
      req_actor ! SpotifyRequest(url, CompletedIndividualArtist)    
    }


    case CompletedIndividualArtist(SpotifyResponse(x, SpotifyRequest(url, _))) => {
      val newest_id = get_latest_artist(x)
      working_map = working_map - url
      completed_map = completed_map + url
      // db_actor ! CheckIfCurrent(url, newest_id, self, CheckArtistResponse)
    }

    case CheckArtistResponse(yes_or_no) => {
    }


    case CheckStatus => {
      if (completed_initial){
        if(working_map.isEmpty){
          log.debug("Completed individuals. Responding to {}", respond_to)
          (respond_to) ! FinishedUrl(work_url, total_individual_requests)   
        }
        else {
          working_map.foreach(x => {
            log.debug("Not completed individual: {}", x)
            self ! HandleIndividualArtist(x)
          })
        }
      }
      else {
        log.debug("Not completed initial")
        self ! HandleJobStartInternal(work_url)
      }
    }
  }
}

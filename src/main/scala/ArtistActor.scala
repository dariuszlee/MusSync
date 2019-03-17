import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import akka.routing.SmallestMailboxPool

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
  case class FirstArtistResponse(res : SpotifyResponse)

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

  var album_actor_map : Map[String, ActorRef] = Map[String, ActorRef]()
  var artist_urls_working : Set[String] = Set[String]()

  val req_actor = context.actorSelection("/user/req_actor")
  val db_actor = context.actorSelection("/user/db-actor")

  var total_individual_requests = 0
  var internal_total_artists : BigDecimal = 0

  var checking_map : Map[String, String] = Map[String,String]()

  override def receive = {
    case HandleJobStart(url) => {
      req_actor ! SpotifyRequest(url, FirstArtistResponse)
    }
    case FirstArtistResponse(SpotifyResponse(x, rep)) => {
      completed_initial = true  
      x \ "artists" \ "total" match {
        case JsDefined(JsNumber(total_artists)) => {
          log.info("TOTAL ARTISTS: {}", total_artists)
          internal_total_artists = total_artists
        }
        case _ => log.error("No total value")
      }
      self ! HandleJobEnd(SpotifyResponse(x, rep))
    }
    case HandleJobStartInternal(url) => {
      log.info("new job {}", url)
      checking_artist = checking_artist + url
      req_actor ! SpotifyRequest(url, HandleJobEnd)
    }
    case HandleJobEnd(SpotifyResponse(x, req)) => {
      x \ "artists" \ "next" match {
        case JsDefined(JsString(x)) => {
          self ! HandleJobStartInternal(x)
        }
        case JsDefined(JsNull) => println("Finished....")
        case _ => log.error("Invalid input")
      }
      for (artist <- (x \ "artists" \ "items").as[Seq[JsObject]]) {
        val artist_id = (artist \ "id").as[String]
        self ! HandleIndividualArtist(artist_id)
      }
      checking_artist = checking_artist - req.uri
    }
    case HandleIndividualArtist(artist_id) => {
      val album_actor = context.actorOf(AlbumActor.props(artist_id, self), 
        s"artist_$artist_id")
      album_actor_map.put(artist_id, album_actor)
      req_actor ! SpotifyRequest(url, CompletedIndividualArtist)    
    }
    case CompletedIndividualArtist(SpotifyResponse(x, SpotifyRequest(url, _))) => {
      val newest_id = get_latest_artist(x)
      working_map = working_map - url
      completed_map = completed_map + url
    }

    case CheckArtistResponse(yes_or_no) => {
    }
    case CheckStatus => {
      if(artist_urls_working.emtpy() && album_actor_map.empty()){
      }
    }
  }
}

object TestArtistActor extends App {
  import ArtistActor._
  implicit val context = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.dispatcher

  val requestActors = context.actorOf(SmallestMailboxPool(5).props(SpotifyRequestActor.props(materializer)), "req_actor")

  val rel_app = context.actorOf(ReleaseActor.props(false), "rel-app")
  val art_actor = context.actorOf(ArtistActor.props("asdf", rel_app, true), "test_artist")  

  art_actor ! HandleJobStart("https://api.spotify.com/v1/me/following?type=artist&limit=20")

  readLine()
  context.terminate()
}

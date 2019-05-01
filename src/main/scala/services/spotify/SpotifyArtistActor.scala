package services.spotify

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

import services.spotify.SpotifyRequestActor._
import db.SpotifyDbActor._

object SpotifyArtistActor {
  def props(url : String, respond_to: ActorRef, is_debug: Boolean, mus_sync_user: String, spot_user_id: String) : Props = Props(new SpotifyArtistActor(url, respond_to, is_debug, mus_sync_user, spot_user_id))

  case class HandleJobStart(url: String)
  case class HandleJobStartInternal(url: String)
  case class HandleJobEnd(x : SpotifyResponse)
  case class HandleIndividualArtist(url: String)
  case class CompletedIndividualArtist(artist_id: String)
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

class SpotifyArtistActor(work_url : String, respond_to: ActorRef, dump_mode: Boolean, mus_sync_user: String, spot_user_id: String) extends Actor with akka.actor.ActorLogging {
  import SpotifyArtistActor._
  import SpotifyAlbumActor._
  import SpotifyReleaseActor._

  var completed_initial = false

  var album_actor_map : HashMap[String, ActorRef] = HashMap[String, ActorRef]()
  var artist_urls_working : Set[String] = Set[String]()

  val req_actor = context.actorSelection("/user/req_actor")
  val db_actor = context.actorSelection("/user/db-actor")

  var internal_total_artists : BigDecimal = 0

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
    case HandleJobEnd(SpotifyResponse(x, req)) => {
      x \ "artists" \ "next" match {
        case JsDefined(JsString(x)) => {
          self ! HandleJobStartInternal(x)
        }
        case JsDefined(JsNull) => log.info("Artists collection finished...")
        case _ => log.error("Invalid input")
      }
      for (artist <- (x \ "artists" \ "items").as[Seq[JsObject]]) {
        val artist_id = (artist \ "id").as[String]
        self ! HandleIndividualArtist(artist_id)
      }
      artist_urls_working = artist_urls_working - req.uri
    }
    case HandleJobStartInternal(url) => {
      artist_urls_working = artist_urls_working + url
      req_actor ! SpotifyRequest(url, HandleJobEnd)
    }
    case HandleIndividualArtist(artist_id) => {
      if(!album_actor_map.contains(artist_id)){
        db_actor ! InsertSpotifyArtist(mus_sync_user, spot_user_id, artist_id)
        val album_actor = context.actorOf(SpotifyAlbumActor.props(artist_id, self, mus_sync_user, spot_user_id, to_dump), 
          s"artist_$artist_id")
        album_actor_map.put(artist_id, album_actor)
        album_actor ! StartAlbumJob
      }
    }
    case CompletedIndividualArtist(artist_id) => {
      album_actor_map = album_actor_map - artist_id
    }
    case CheckStatus => {
      if(artist_urls_working.isEmpty && album_actor_map.isEmpty){
        log.info("Check Status: Completely finished")
        respond_to ! FinishedUrl(work_url, internal_total_artists.intValue)
      }
      else {
        log.info("Check Status: artist_urls_left {} and album actors {}", artist_urls_working.size, album_actor_map.size)
        for(artist_collect_req <- artist_urls_working) {
          self ! HandleJobStartInternal(artist_collect_req)
        }
        for((_, album_actor) <- album_actor_map) {
          album_actor ! CheckAlbumStatus
        }
      }
    }
  }
}

object TestArtistActor extends App {
  import SpotifyArtistActor._
  implicit val context = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.dispatcher

  val mus_sync_user = "mus_sync_user"
  val spot_user_id = "spot_user_id"

  val requestActors = context.actorOf(SmallestMailboxPool(5).props(SpotifyRequestActor.props(materializer)), "req_actor")

  val rel_app = context.actorOf(SpotifyReleaseActor.props(false, mus_sync_user, spot_user_id), "rel-app")
  val art_actor = context.actorOf(SpotifyArtistActor.props("asdf", rel_app, true, mus_sync_user, spot_user_id), "test_artist")  

  import scala.concurrent.duration._
  art_actor ! HandleJobStart("https://api.spotify.com/v1/me/following?type=artist&limit=20")
  context.scheduler.schedule(3000 milliseconds, 3000 milliseconds, art_actor, SpotifyArtistActor.CheckStatus)

  readLine()
  context.terminate()
}

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

class ArtistActor(work_url : String, respond_to: ActorRef, dump_mode: Boolean) extends Actor with akka.actor.ActorLogging {
  import ArtistActor._
  import AlbumActor._
  import ReleaseActor._

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
    case HandleJobStartInternal(url) => {
      artist_urls_working = artist_urls_working + url
      req_actor ! SpotifyRequest(url, HandleJobEnd)
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
    case HandleIndividualArtist(artist_id) => {
      if(!album_actor_map.contains(artist_id)){
        val album_actor = context.actorOf(AlbumActor.props(artist_id, self), 
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
          album_actor ! AlbumActor.CheckAlbumStatus
        }
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

  import scala.concurrent.duration._
  art_actor ! HandleJobStart("https://api.spotify.com/v1/me/following?type=artist&limit=20")
  context.scheduler.schedule(3000 milliseconds, 3000 milliseconds, art_actor, ArtistActor.CheckStatus)

  readLine()
  context.terminate()
}

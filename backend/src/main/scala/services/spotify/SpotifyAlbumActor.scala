package services.spotify

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import play.api.libs.json.JsObject
import play.api.libs.json.JsNull
import play.api.libs.json.JsString
import play.api.libs.json.JsDefined
import play.api.libs.json.JsArray

import SpotifyRequestActor._

import db.SpotifyDbActor._

object SpotifyAlbumActor {
  def props(artist_id: String, respond_to: ActorRef, mus_sync_id: String, spotify_user_id: String, to_dump: Boolean) : Props = Props(new SpotifyAlbumActor(artist_id, respond_to, mus_sync_id, spotify_user_id, to_dump))

  case object StartAlbumJob
  case class FirstAlbumResponse(res: SpotifyResponse)

  case class GetAlbum(uri: String)
  case class GetAlbumResponse(res: SpotifyResponse)
  case class HandleDumpAlbumRequest(res: SpotifyResponse)
  case class HandleAlbumList(albums : Seq[AlbumInfo])
  case class HandleNext(next_uri: Option[String])

  case class AlbumInfo(mus_id: String, spotify_id: String, spotify_album_id: String)
  case object CheckAlbumStatus
  case object Shutdown
}

class SpotifyAlbumActor(artist_id: String, respond_to: ActorRef, mus_sync_id: String, spotify_user_id: String, to_dump: Boolean) extends Actor with akka.actor.ActorLogging {
  import services.spotify.SpotifyAlbumActor._
  import services.spotify.SpotifyArtistActor._

  val url = s"https://api.spotify.com/v1/artists/$artist_id/albums"
  val req_actor = context.actorSelection("/user/req_actor")
  val db_actor = context.actorSelection("/user/db-actor")

  var total_albums = 0

  var album_requests_urls : Set[String] = Set[String]()

  override def receive = {
    case StartAlbumJob => {
      album_requests_urls += url
      req_actor ! SpotifyRequest(url, FirstAlbumResponse)
    }
    case FirstAlbumResponse(SpotifyResponse(res, req)) => {
      total_albums = (res \ "total").as[Int]
      self ! GetAlbumResponse(SpotifyResponse(res, req))
    }
    case GetAlbum(get_album_url: String) => {
      album_requests_urls = album_requests_urls + get_album_url
      req_actor ! SpotifyRequest(get_album_url, GetAlbumResponse)
    }
    case GetAlbumResponse(SpotifyResponse(res, req)) => {
      album_requests_urls = album_requests_urls - req.uri

      if(to_dump){
        self ! HandleDumpAlbumRequest(SpotifyResponse(res, req))
      }
      else {
        val albums = (res \ "items").as[Seq[JsObject]].map(album_js => {
          AlbumInfo(mus_sync_id, spotify_user_id, (album_js \ "id").as[String])
        })
        self ! HandleAlbumList(albums)
      }

      val next_data = (res \ "next").asOpt[String]
      self ! HandleNext(next_data)
    }
    case HandleAlbumList(albums) => {
      albums match {
        case Seq() => println("empty")
        case head +: tail => {
          db_actor ! CheckAlbumExistence(head)
          self ! HandleAlbumList(tail)
        }
        case _ => log.error("What is this???: {}", albums)
      }
    }
    case HandleDumpAlbumRequest(SpotifyResponse(res,req)) => {
      (res \ "items").as[Seq[JsObject]].foreach(album => {
        val album_id = (album \ "id").as[String]
        db_actor ! InsertSpotifyAlbum(mus_sync_id, spotify_user_id, album_id, AlbumTag.Old)
      })
      val next_data = (res \ "next").asOpt[String]
      self ! HandleNext(next_data)
    }
    case HandleNext(next_option) => {
      next_option match {
        case Some(next_uri) => {
          self ! GetAlbum(next_uri)
        }
        case None => {
          log.info("Getting albums from artists collection finished...")
          self ! Shutdown // We want the shutdown message to be the last message sent 
        }
      }
    }
    case AlbumExists(album_info) => {
      log.info("Found Existing album: {}", album_info)
      self ! Shutdown
    }
    case CheckAlbumStatus => {
      for(album_request <- album_requests_urls){
        self ! GetAlbum(album_request)
      }
    }
    case Shutdown => {
      log.info("Shuting down {}...", artist_id)
      respond_to ! CompletedIndividualArtist(artist_id)
      context.stop(self)
    }
  }
}

object AlbumActorChecker {
  def props : Props = Props(new AlbumActorChecker())

  case object DefaultAction
}

class AlbumActorChecker extends Actor with akka.actor.ActorLogging {
  import AlbumActorChecker._

  override def receive = {
    case DefaultAction => {}
  }
}

object TestAlbumActor extends App {
  implicit val context = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.dispatcher
  import SpotifyAlbumActor._
  import db.SpotifyDbActor

  import akka.testkit.TestProbe
  val probe : TestProbe = new TestProbe(context);
  val mock : ActorRef = probe.ref;

  import akka.routing.SmallestMailboxPool
  val requestActors = context.actorOf(SmallestMailboxPool(5).props(SpotifyRequestActor.props(materializer)), "req_actor")
  val db_actor = context.actorOf(SpotifyDbActor.props("localhost"), "db-actor")

  val alb_act = context.actorOf(SpotifyAlbumActor.props("1vCWHaC5f2uS3yhpwWbIA6", mock, "mus_sync_user", "spot_user_id", false), "alb-act")  
  alb_act ! StartAlbumJob

  readLine()
  context.terminate()
}

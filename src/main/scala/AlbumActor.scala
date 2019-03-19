import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import play.api.libs.json.JsObject
import play.api.libs.json.JsNull
import play.api.libs.json.JsString
import play.api.libs.json.JsDefined

import SpotifyRequestActor._

object AlbumActor {
  def album_actor_dump_props(artist_id: String, respond_to: ActorRef) : Props = Props(new AlbumActorDumper(artist_id, respond_to))

  case object StartAlbumJob
  case class FirstAlbumResponse(res: SpotifyResponse)

  case class GetAlbum(uri: String)
  case class GetAlbumResponse(res: SpotifyResponse)
  case class HandleAlbumList(albums : Seq[JsObject])

  case object CheckAlbumStatus
  case object Shutdown
}

class AlbumActorDumper(artist_id: String, respond_to: ActorRef) extends Actor with akka.actor.ActorLogging {
  import AlbumActor._
  import ArtistActor._

  val url = s"https://api.spotify.com/v1/artists/$artist_id/albums"
  val req_actor = context.actorSelection("/user/req_actor")
  val db_actor = context.actorSelection("/user/db_actor")

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
      self ! HandleAlbumList((res \ "items").as[Seq[JsObject]])

      res \ "next" match {
        case JsDefined(JsString(next)) => {
          self ! GetAlbum(next)
        }
        case JsDefined(JsNull) => {
          log.info("Getting albums from artists collection finished...")
          respond_to ! CompletedIndividualArtist(artist_id)
          self ! Shutdown // We want the shutdown message to be the last message sent 
        }
      }
    }
    case HandleAlbumList(albums) => {
      import SpotifyDbActor.{DumpAlbums}
      db_actor ! DumpAlbums("fake_user", albums.map(x => ((x \ "id").as[String])))
    }
    case CheckAlbumStatus => {
      for(album_request <- album_requests_urls){
        self ! GetAlbum(album_request)
      }
    }
    case Shutdown => {
      log.info("Shutding down {}...", artist_id)
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
  import AlbumActor._

  import akka.testkit.TestProbe
  val probe : TestProbe = new TestProbe(context);
  val mock : ActorRef = probe.ref;

  import akka.routing.SmallestMailboxPool
  val requestActors = context.actorOf(SmallestMailboxPool(5).props(SpotifyRequestActor.props(materializer)), "req_actor")
  val db_actor = context.actorOf(SpotifyDbActor.props, "db_actor")

  val alb_act = context.actorOf(AlbumActor.album_actor_dump_props("1vCWHaC5f2uS3yhpwWbIA6", mock), "alb-act")  
  alb_act ! StartAlbumJob

  readLine()
  context.terminate()
}

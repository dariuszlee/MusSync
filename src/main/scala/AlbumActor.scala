import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import play.api.libs.json.JsObject

import SpotifyRequestActor._

object AlbumActor {
  def props(artist_id: String, respond_to: ActorRef) : Props = Props(new AlbumActor(artist_id, respond_to))

  case object StartAlbumJob
  case class FirstAlbumResponse(res: SpotifyResponse)

  case class GetAlbum(uri: String)
  case class GetAlbumResponse(res: SpotifyResponse)
  case class HandleAlbumList(albums : Seq[JsObject])
}

class AlbumActor(artist_id: String, respond_to: ActorRef) extends Actor with akka.actor.ActorLogging {
  import AlbumActor._

  val url = s"https://api.spotify.com/v1/artists/$artist_id/albums"
  val req_actor = context.actorSelection("/user/req_actor")

  var total_albums = 0

  override def receive = {
    case StartAlbumJob => {
      req_actor ! SpotifyRequest(url, FirstAlbumResponse)
    }
    case FirstAlbumResponse(SpotifyResponse(res, req)) => {
      total_albums = (res \ "total").as[Int]
      self ! GetAlbumResponse(SpotifyResponse(res, req))
    }
    case GetAlbum(uri: String) => {
      req_actor ! SpotifyRequest(url, GetAlbumResponse)
    }
    case GetAlbumResponse(SpotifyResponse(res, req)) => {
      val next = (res \ "next").as[String]
      self ! GetAlbum(next)
      self ! HandleAlbumList((res \ "items").as[Seq[JsObject]])
    }
    case HandleAlbumList(albums) => {
      for(i <- albums) {
        println((i \ "id").as[String])
      }
    }
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

  val alb_act = context.actorOf(AlbumActor.props("1vCWHaC5f2uS3yhpwWbIA6", mock), "alb-act")  
  import akka.routing.SmallestMailboxPool
  val requestActors = context.actorOf(SmallestMailboxPool(5).props(SpotifyRequestActor.props(materializer)), "req_actor")
  alb_act ! StartAlbumJob

  readLine()
  context.terminate()
}

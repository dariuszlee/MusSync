import akka.pattern.pipe
import akka.pattern.ask
import scala.concurrent.duration._
import akka.util.Timeout
import scala.util.Success
import scala.util.Failure

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props

import play.api.libs.json.JsValue

object SpotifyArtist {
  def props : Props = Props(new SpotifyArtist())
  def AlbumsUri(id : String, limit : Int) : String = s"https://api.spotify.com/v1/artists/$id/albums?limit=$limit"

  case class GetLatest(artistId : String)
}

class SpotifyArtist extends Actor with ActorLogging {
  import SpotifyArtist._
  import SpotifyRequestActor.SpotifyResponse
  val reqActor = context.actorSelection("/user/request-actors")
  implicit val timeout : Timeout = 10 seconds
  import context.dispatcher

  def receive = {
    case GetLatest(id) => {
      // val albumUri = SpotifyArtist.AlbumsUri(id, 1)
      // ask(reqActor, SpotifyRequestActor.SpotifyRequest(albumUri)).pipeTo(self)
    }
    case SpotifyResponse(latest, _) => {
      val albName = ((latest \ "items")(0) \ "name" ).as[JsValue]
      val artName = (((latest \ "items")(0) \ "artists")(0) \ "name").as[JsValue]
      log.info("Success: Album name: {} by author: {}", albName, artName)
    }
  }
}


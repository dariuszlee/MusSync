import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

object AlbumActor {
  def props(artist_id: String, respond_to: ActorRef) : Props = Props(new AlbumActor(artist_id, respond_to))

  case class GetAlbum(uri: String)
  case object StartAlbumJob
}

class AlbumActor(artist_id: String, respond_to: ActorRef) extends Actor with akka.actor.ActorLogging {
  import AlbumActor._

  val req_actor = context.actorSelection("/user/req_actor")
  val url = s"https://api.spotify.com/v1/artists/$artist_id/albums"

  override def receive = {
    case StartAlbumJob => {
      println(url)
    }
    case GetAlbum(uri: String) => {
    }
  }
}

object TestAlbumActor extends App {
  implicit val context = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.dispatcher

  import akka.testkit.TestProbe
  val probe : TestProbe = new TestProbe(context);
  val mock : ActorRef = probe.ref;

  import AlbumActor._
  val alb_act = context.actorOf(AlbumActor.props("asdfasdf", mock), "alb-act")  
  alb_act ! StartAlbumJob

  readLine()
  context.terminate()
}

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

object SpotifyDbActor {
  case object InsertSpotifyItem
}

class SpotifyDbActor extends Actor {
  import SpotifyDbActor._
  def receive = {
    case InsertSpotifyItem => println("")
  }
}

object SpotifyDbTests extends App {
  import SpotifyDbActor._
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val dbActor = system.actorOf(Props(new SpotifyDbActor()), "spotifyDb")

  dbActor ! InsertSpotifyItem

  readLine()
  system.terminate()
}

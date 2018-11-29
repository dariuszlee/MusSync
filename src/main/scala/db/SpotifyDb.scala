import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

object SpotifyDb {
  case object InsertDbItem
}

class SpotifyDb extends Actor {
  import SpotifyDb._
  def receive = {
    case InsertDbItem => { 
      println("Inserting item")
    }
  }
}

object SpotifyDbTests extends App {
  import SpotifyDb._
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val userFollows = system.actorOf(Props(new SpotifyDb()), "spotifyDb")

  userFollows ! InsertDbItem

  readLine()
  system.terminate()
}

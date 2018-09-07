import akka.actor._

object MusSyncApp extends App {
    val system = ActorSystem("HttpAsyncSystem")

    val master = system.actorOf(Props(new HttpCreator()))
    master ! HandleHttp(5) 
}

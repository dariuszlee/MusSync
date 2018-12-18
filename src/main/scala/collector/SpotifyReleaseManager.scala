import SpotifyRequestActor._

import scala.util.Try
import scala.util.Success
import scala.util.Failure

import akka.routing.SmallestMailboxPool

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import play.api.libs.json.JsValue
import play.api.libs.json.JsArray
import play.api.libs.json.JsNull
import play.api.libs.json.JsDefined

object LogActor {
  case object LogSomething
}

class LogActor extends Actor with akka.actor.ActorLogging {
  import LogActor._

  def receive = {
    case LogSomething => {
      log.info("Logging something here: ")
      log.debug("Lets log something else: {}", 2)
    }
  }
}

object SpotifyFollow {
  case class GetFollows(uri : Option[String])
  val getFollowersUri : String = "https://api.spotify.com/v1/me/following?type=artist"
}

class SpotifyFollow(reqActor : ActorRef) extends Actor {
  import SpotifyFollow._
  import context.dispatcher
  implicit val timeout : Timeout = 3 seconds

  val spotifyFollowActors = context.actorSelection("akka://default/user/spotifyFollow")

  def receive = {
    case GetFollows(uri) => {
      val requestUri = uri getOrElse getFollowersUri
      ask(reqActor, SpotifyRequestActor.SpotifyRequest(requestUri)) onComplete({
        case Success(SpotifyResponse(y)) => {
          Try((y \ "artists" \ "next")) getOrElse None match {
            case JsDefined(JsNull) => println("Finished Getting Next's Api Calls.")
            case JsDefined(x : JsValue) => {
              val nextUri = x.as[String]
              spotifyFollowActors ! GetFollows(Some(nextUri))
            }
            case None => println("Operation failed")
          }
          Try(y \ "artists" \ "items") getOrElse None match {
            case JsDefined(artists : JsValue) => {
              // val nextUri = x.as[String]
              // spotifyFollowActors ! GetFollows(Some(nextUri))
              for(artist <- artists.as[List[JsValue]]){
                println(artist \ "id")

              }
            }
            case None => println("Operation failed")
          }
        }
        case Success(x) => println("Unknown response type: " + x)
        case Failure(x) => println(x)
      })
    }
  }
}

object SpotifyReleaseManager extends App {
  import SpotifyFollow._
  import LogActor._
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  // val routeRequest = system.actorOf(SmallestMailboxPool(5).props(Props[SpotifyRequestActor]), "requestRouter")


  // val userFollows = system.actorOf(Props(new SpotifyFollow(routeRequest)), "spotifyFollow")
  // userFollows! GetFollows(None)
  val logActor = system.actorOf(Props[LogActor], "logActor")
  logActor ! LogSomething

  readLine()
  system.terminate()
}

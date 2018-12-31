import akka.pattern.pipe
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import scala.util.Success
import scala.util.Failure

import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._

import akka.actor.Props
import akka.actor.Actor
import akka.actor.ActorRef
import akka.stream.ActorMaterializer
import akka.stream.Materializer
import akka.actor.ActorSystem

import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.libs.json.JsDefined

import akka.event.LoggingAdapter

import akka.http.scaladsl.Http
import akka.util.ByteString
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

object SpotifyRequestActor {
  case class SpotifyRequest(uri : String)
  case class SpotifyRequestWithToken(token: String, uri: String, responseTo: ActorRef)
  // case class SpotifyReplyTo(toReply: ActorRef, )
  case class SpotifyResponse(res : JsValue)
  case object SuccessfulRequest
  case object FailedRequest

  val auth_api = "http://localhost:8080"
  val token_path = "/session"
  val token_uri = auth_api + token_path

  def props(implicit materializer: ActorMaterializer) : Props = Props(new SpotifyRequestActor()(materializer))
  
  def refresh_token(log : LoggingAdapter)(implicit mat: Materializer, context: ExecutionContext, system: ActorSystem) : Future[String] = {
    AkkaHttpUtils.request_json(token_uri).map(x => {
        (x \ "token").as[String]
      }
    )
  }
}

class SpotifyRequestActor()(implicit mat: ActorMaterializer) extends Actor with akka.actor.ActorLogging {
  import SpotifyRequestActor._
  import TokenActor._
  import context._
  implicit val timeout : akka.util.Timeout = 5 second
  val authActor = context.actorSelection("/user/auth_actor")
  
  var request_num = 0
  def receive = {
    case SpotifyRequestWithToken(token, uri, responseActor) => {
      request_num += 1
      AkkaHttpUtils.request_oauth_raw(uri, token, request_num).map(res => {
        // SpotifyReplyTo(responseActor, res)
      }).pipeTo(self)
    }
    // case SpotifyResponse(x) => x match {
    //   case (StatusCodes.Unauthorized, _, _, _, req) => {
    //     log.error("Unauthorized Request: {}", req)
    //     authActor ! RefreshToken
    //     self ! SpotifyRequest(uri)
    //   }
    //   case ((StatusCodes.TooManyRequests, headers, data: Future[String], p, req)) => {
    //     log.info("Number of requests before failure: {}", req)
    //     if(request_num > 100){
    //       log.info("SLEEPING-----")
    //       request_num = 0
    //       Thread.sleep(1000)
    //     }
    //     self ! FailedRequest
    //   }
    //   case (StatusCodes.Success(_), _, data: Future[String], _, req) => {
    //     log.info("Request: {}", req)
    //     data.onComplete({
    //       case Success(dataString) => responseActor ! SpotifyResponse(Json.parse(dataString))
    //     })
    //   }
    // }
    case SpotifyRequest(uri) => {
      val responseActor = sender()
      ask(authActor, GetToken)
        .map((token) => {
          val session = token.asInstanceOf[SessionToken]
          SpotifyRequestWithToken(session.token, uri, responseActor)
        }).pipeTo(self)
    }
  }
}

object SpotifyRequestTest extends App {
  import TokenActor._
  val testUri = "https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1"

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout : akka.util.Timeout = 5 second

  val authActor = system.actorOf(TokenActor.props, "auth_actor")
  val reqActor = system.actorOf(SpotifyRequestActor.props(materializer), "spotify_requester")

  ask(reqActor, SpotifyRequestActor.SpotifyRequest(testUri)) onComplete({
    case Success(x) => println(x)
    case Failure(x) => println(x)
  })
  // reqActor ! SpotifyRequestActor.SpotifyRequest("https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1")

  readLine()
  system.terminate()
}

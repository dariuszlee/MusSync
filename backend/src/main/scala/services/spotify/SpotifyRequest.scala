package services.spotify

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

import utils.AkkaHttpUtils

object SpotifyRequestActor {
  case class SpotifyRequest(uri : String, respond_with : SpotifyResponse => Object)
  case class SpotifyResponse(res : JsValue, req : SpotifyRequest)

  case class SpotifyRawRequest(uri : String, respond_to : ActorRef, respond_with : SpotifyResponse => Object)
  case class SpotifyRawResponse(res : HttpResponse, req_details : SpotifyRawRequest)

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

  val spotify_token_utils = new SpotifyTokenUtils()
  
  var request_num = 0
  // var token = spotify_token_utils.refresh_token()
  var token = "aaaa"
  def receive = {
    case SpotifyRequest(uri, respond_with) => {
      self ! SpotifyRawRequest(uri, sender(), respond_with)
    }
    case SpotifyRawRequest(uri, respond_to, respond_with) => {
      AkkaHttpUtils.request_oauth_raw(uri, token, request_num).map(x => SpotifyRawResponse(x, SpotifyRawRequest(uri, respond_to, respond_with))).pipeTo(self)
    }
    case SpotifyRawResponse(HttpResponse(StatusCodes.Unauthorized, headers, entity, _), req_details) => {
      token = spotify_token_utils.refresh_token()
      self ! req_details
    }
    case SpotifyRawResponse(HttpResponse(StatusCodes.TooManyRequests, headers, entity, _), req_details) => {
      self ! req_details
    }
    case SpotifyRawResponse(HttpResponse(StatusCodes.Success(_), headers, entity, _), req_det) => {
      entity.dataBytes.runFold[String]("")(_ + _.utf8String).map({
        x => req_det.respond_with(SpotifyResponse(Json.parse(x), SpotifyRequest(req_det.uri, req_det.respond_with)))
      }).pipeTo(req_det.respond_to)
    }
    case SpotifyRawResponse(failed_msg, req_details) => {
      println("Unmatched: ", failed_msg)
      println("Request_details", req_details)
      self ! req_details
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

  val reqActor = system.actorOf(SpotifyRequestActor.props(materializer), "spotify_requester")

  case class TestResponse(x: SpotifyRequestActor.SpotifyResponse)

  ask(reqActor, SpotifyRequestActor.SpotifyRequest(testUri, r => TestResponse(r))) onComplete({
    case Success(x) => println(x)
    case Failure(x) => println(x)
  })

  readLine()
  system.terminate()
}

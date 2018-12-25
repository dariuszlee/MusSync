import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}

import akka.http.scaladsl.Http
import akka.util.ByteString
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

import play.api.libs.json.Json
import play.api.libs.json.JsValue

object AkkaHttpUtils {
  def post(uri: String, data: ByteString)(implicit materializer: Materializer, context: ExecutionContext, system: ActorSystem)
 : Future[JsValue] = {
    val contentType = ContentType.parse("application/x-www-form-urlencoded") match {
      case Right(x) => x
    }
    val request = HttpRequest(uri = uri).withMethod(HttpMethods.POST).withEntity(contentType, data)
    Http().singleRequest(request).flatMap(x => {
      x.entity.dataBytes.map(x => x.utf8String).runFold("")(_ ++ _)
    }).map(x => Json.parse(x))
  }


  def request_json(uri: String)(implicit materializer: Materializer, context: ExecutionContext, system: ActorSystem)
  : Future[JsValue] = {
    request(uri).flatMap(x => {
      Future(Json.parse(x))
      // Json.parse(x)
    })
  }

  def request(uri: String)(implicit materializer: Materializer, context: ExecutionContext, system: ActorSystem)
  : Future[String] = {
    val request = HttpRequest(uri = uri)
    get_response(request)
  }

  def request_oauth(uri: String, token: String)(implicit materializer: Materializer, context: ExecutionContext, system: ActorSystem) 
    : Future[String] = {
    val request = HttpRequest(uri = uri).addCredentials(new OAuth2BearerToken(token))
    get_response(request)
  }

  def request_oauth_raw(uri: String, token: String)(implicit materializer: Materializer, context: ExecutionContext, system: ActorSystem) 
    : Future[Tuple2[StatusCode, Future[String]]] = {
    val request = HttpRequest(uri = uri).addCredentials(new OAuth2BearerToken(token))
    get_response_raw(request)
  }

  def get_response_raw(req: HttpRequest)(implicit materializer: Materializer, context: ExecutionContext, system: ActorSystem)
 : Future[Tuple2[StatusCode, Future[String]]] = {
    Http().singleRequest(req).flatMap(x => {
      Future((x.status, x.entity.dataBytes.map(x => x.utf8String).runFold("")(_ ++ _)))
    })
  }

  def get_response(req: HttpRequest)(implicit materializer: Materializer, context: ExecutionContext, system: ActorSystem)
 : Future[String] = {
    Http().singleRequest(req).flatMap(x => {
      x.entity.dataBytes.map(x => x.utf8String).runFold("")(_ ++ _)
    })
  }
}

object AkkHttpUtilsApp extends App {
  import scala.util.Success
  import scala.util.Failure

  val testUri = "https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1"
  // val testUri = "http://localhost:8080/session"

  implicit val system = ActorSystem()
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  AkkaHttpUtils.request_oauth_raw(testUri, "asdf") onComplete {
    case Success(x) => println(x)
    case Failure(x) => println(x)
  }

  readLine()
  system.terminate()
}

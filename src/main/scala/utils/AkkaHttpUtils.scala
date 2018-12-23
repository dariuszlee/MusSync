import scala.concurrent.Future

import akka.actor.ActorSystem
import akka.stream.{Materializer, ActorMaterializer}

import akka.http.scaladsl.Http
import akka.util.ByteString
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._

object AkkaHttpUtils {
  def request_oauth(uri: String, token: String)(implicit materializer: Materializer, context: ActorSystem) 
    : Future[String] = {
    import context._

    val request = HttpRequest(uri = uri).addCredentials(new OAuth2BearerToken(token))
    val responseFuture = Http().singleRequest(request)
    responseFuture.flatMap(x => {
      x.entity.dataBytes.map(x => x.utf8String).runFold("")(_ ++ _)
    })
  }
}

object AkkHttpUtilsApp extends App {
  import scala.util.Success
  import scala.util.Failure
  val testUri = "https://api.spotify.com/v1/artists/6rvxjnXZ3KPlIPZ8IP7wIT/albums?limit=1"

  implicit val system = ActorSystem()
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  AkkaHttpUtils.request_oauth(testUri, "asdf") onComplete {
    case Success(x) => println(x)
    case Failure(x) => println(x)
  }

  readLine()
  system.terminate()
}

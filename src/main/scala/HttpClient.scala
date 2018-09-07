import com.softwaremill.sttp._
import akka.actor._
import akka.routing._

case class HandleHttp(num: Integer)
case class Fetch(id : Integer, url: String)

class HttpClient {
  def fetch(url : String) : String = {
    val request = sttp.get(uri"$url")
    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()

    println(response.unsafeBody)

    return url
  }
}

class HttpWorker extends Actor {
  val client = new HttpClient()
  def receive = {
    case Fetch(id, url) => {
      Console.println("Received in" + id)
      client.fetch(url)
    }
  }
}

class HttpCreator extends Actor {
  val workerPool = context.actorOf(RoundRobinPool(5).props(Props[HttpWorker]), "Router1")
  val url = "https://api.coinbase.com/v2/time"

  def receive = {
    case HandleHttp(num) => for(i <- 0 to num) { 
      Console.println("Routing: " + i)
      workerPool ! Fetch(i, url)
    }
  }
}

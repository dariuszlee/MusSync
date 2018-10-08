package services

import akka.NotUsed
import akka.actor.ActorSystem

import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.RunnableGraph
import akka.stream.scaladsl.Keep

import scala.concurrent.Future
import scala.List

import com.softwaremill.sttp._
import play.api.libs.json.JsValue
import play.api.libs.json.Json

final case class SpotifyRelease(name : String)

object SpotifyUtility {
  val apiEndpoint = "https://api.spotify.com"
  val scopes = "user-read-private user-read-email"
  val client_id = "40b76927fb1a4841b2114bcda79e829a"
  val client_secret = "7eb1825fb44845b8bd463f9e883fa9a9"
  val callback = "https://example.com/callback"

  val fullUri = "https://accounts.spotify.com/en/authorize?client_id=40b76927fb1a4841b2114bcda79e829a&response_type=code&redirect_uri=https:%2F%2Fexample.com%2Fcallback&scope=user-read-private%20user-read-email&state=34fFs29kd09&show_dialog=true"

  def collect() : List[SpotifyRelease] = {
    val sourceData = SpotifyRelease("SomeGuy") :: SpotifyRelease("SomeOtherGuy") :: Nil
    return sourceData
  }

  def authorization() : Unit = {
    val uri = uri"https://accounts.spotify.com/authorize/?client_id=$client_id&response_type=code&redirect_uri=$callback&scope=$scopes&state=34fFs29kd09"
    Console.println(uri)

    val request = sttp.get(uri)
    implicit val backend = HttpURLConnectionBackend()
    val response = request.send()
    Console.println(response.unsafeBody)
    Console.println(response.headers)
    Console.println(response.code)
  }

  def tokenize() : JsValue = {
    val authCode = "AQAtkal0xjDICmnxzkwLw3K2VDQurq7iDR_wWJ9uWD70TLPfxzQykebwKXWQiml9OADAfYje7sGqiRrUMWXucyyi_fy3OZ8jXN4zrMxD28XjqnensH0dc46NeJ31INxHzW8WzhnNhYgty1KffZT0Dw0kOxkG5a9HQEjORJEEA2wFBjNX3MxfrN80Os1FrPvPRu7iyOff-8WLcbrb99UBOb1BbeVlyq6vggyUmwPs3AJd2mtLmer4yw"
    val tokenUri = uri"https://accounts.spotify.com/api/token"
    val map : Map[String, String] = Map("grant_type" -> "authorization_code", "code" -> authCode, "redirect_uri" -> callback, "client_id" -> client_id, "client_secret" -> client_secret)

    implicit val backend = HttpURLConnectionBackend()
    val request = sttp.body(map).post(tokenUri)
    val response = request.send()
    return response.body match {
      case Left(x) => Json.parse(x)
      case Right(x) => Json.parse(x)
    }
  }
}

object SpotifyTest extends App {

    val response = SpotifyUtility.tokenize()
    val token = (response \ "access_token").asOpt[String] match {
      case None => "No token"
      case Some(s) => s
    }

    // implicit val system = ActorSystem("Spotify-Api-Test")
    // implicit val materializer = ActorMaterializer()

    // val sourceData = SpotifyUtility.collect

    // val source : Source[SpotifyRelease, NotUsed] = Source(sourceData)
    // val sink = Sink.foreach(( x : SpotifyRelease) => {
    //   Console.println(x.name)
    // })

    // val runnable = source.toMat(sink)(Keep.right)
    // val output = runnable.run()
}

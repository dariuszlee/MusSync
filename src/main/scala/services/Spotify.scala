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

final case class SpotifyRelease(name : String)

object SpotifyUtility {
  val apiEndpoint = "https://api.spotify.com"
  val scopes = "user-read-private user-read-email"
  val client_id = "40b76927fb1a4841b2114bcda79e829a"
  val client_secret = "7eb1825fb44845b8bd463f9e883fa9a9"
  val callback = "https://example.com/callback"

  val fullUri = "https://accounts.spotify.com/authorize/?client_id=40b76927fb1a4841b2114bcda79e829a&response_type=code&redirect_uri=https://example.com&scope=user-read-private+user-read-email&state=34fFs29kd09&show_dialog=true"

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

  def tokenize() : Unit = {
    val authCode = "AQCBuRhuEvawEKK7-pAb6z0fnCb4MAXJRRktvQsTJfE-TaCpW3nu0dyvMmJZZLA_LmemunqJqeuadXWcp_zsa5pWVbgc0QlU9gyQRkxbEDxt4SK-87N9SIxwaH8leVoIMyHdvO3n8Cd5W7xzwNinAeP9kjGaKnu0AZYqqqSt-r_DxIg_YVY5LCkesO7aOZLbT3GBvmo33H2TuDXcKjMT0eTaKV4z2Rruc8gWi71TNguxnFaUyIouDg"
    val tokenUri = uri"https://accounts.spotify.com/api/token"
    val map : Map[String, String] = Map("grant_type" -> "authorization_code", "code" -> authCode, "redirect_uri" -> callback, "client_id" -> client_id, "client_secret" -> client_secret)

    implicit val backend = HttpURLConnectionBackend()
    val request = sttp.body(map).post(tokenUri)
    val response = request.send()
    Console.println(response)
  }
}

object SpotifyTest extends App {
    SpotifyUtility.tokenize()

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

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

  val fullUri = "https://accounts.spotify.com/en/authorize?client_id=40b76927fb1a4841b2114bcda79e829a&response_type=code&redirect_uri=https:%2F%2Fexample.com%2Fcallback&scope=user-follow-read%20user-read-email&state=34fFs29kd09&show_dialog=true"

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
    val authCode = "AQDprw8MwhpSFAJeY2DsvwM2V7tt1woAn45BauK-TFvtjSQCG6UFjBM1mIzVG2r8uKSup4RA5Eo9368vOmqcx7fQ483dAI_BGz2KqN2qo7M4gqkC2w5O8zg2DwCTGbX5PTHRWFPuGajewssvz6SdH4bBD6QlMHhjrm3gZeQBBmiDUCA91x8AgzdB7NorhZmq5QPMe0uJgIdNPsPlSQ9TpQ7X19WqPdZeJKdnljVkVK2dTFpn28X-"
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

  def get_artists(token : String) : Unit = {
    val artistString = apiEndpoint + "/v1/me/following?type=artist"
    val artistUri = uri"$artistString"
    
    implicit val backend = HttpURLConnectionBackend()
    val request = sttp.auth.bearer(token).get(artistUri)
    val response = request.send()

    Console.println(response)
  }
}



object SpotifyTest extends App {

    // Get a token
    // val response = SpotifyUtility.tokenize()
    // val token = (response \ "access_token").asOpt[String] match {
    //   case None => "No token"
    //   case Some(s) => s
    // }

    // Console.println(token)
    // Get a token: FINISHED

    val token = "BQD9s6GQPbA1RSktPxfMHKT4Y-sESDLqxZa6ChVLStuVl9Q7H0gd74wZ8XsjDx5U4obdKUaAwfOi59Gu5kW3r3rbesxZN-dy69tCIUBJp_QOLJydlwhjkq0sF6D10eneOWE5BFFGSx1Xk-o9gq1ZQ1i2MBWwQFk"

    SpotifyUtility.get_artists(token) 
    

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

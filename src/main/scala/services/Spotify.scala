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
final case class SpotifyPlaylist(name : String)

object SpotifyUtility {
  val apiEndpoint = "https://api.spotify.com"
  val scopes = "user-read-private user-read-email"
  val client_id = "40b76927fb1a4841b2114bcda79e829a"
  val client_secret = "7eb1825fb44845b8bd463f9e883fa9a9"
  val callback = "https://example.com/callback"

  val fullUri = "https://accounts.spotify.com/en/authorize?client_id=40b76927fb1a4841b2114bcda79e829a&response_type=code&redirect_uri=http:%2F%2Flocalhost:8080&scope=user-follow-read%20user-read-email&state=34fFs29kd09&show_dialog=true"

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
    val authCode = "AQB4-YeK2K6iSmvlDtbfoIsMZkMNHJjOP0sxRA9gdaZJe1SXIKNO9QDcGYpmFkX9fg1aqrdgMzGqi9vQsDYQ04jI4XF5MLbakKHEF1sNNIhmbl_rpBX3N0pCsN02PYJI7mMW32bAzNWBDLQRIsV1LLqTgCEsupAkX-6tMlgZBwh3NgxVQ92R5apZw1nTPkEICUcfDl2wnf43uP3AGQPQIAxcn7__u8_N434sYJPl7w0ACCD-p3vJ"
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
    // val artistString = apiEndpoint + "/v1/me/following?type=artist"
    val artistString = apiEndpoint + "/v1/fishehh/following?type=artist"
    val artistUri = uri"$artistString"
    
    implicit val backend = HttpURLConnectionBackend()
    val request = sttp.auth.bearer(token).get(artistUri)
    val response = request.send()

    Console.println(response)
  }

  // def get_playlists(token : String, user : String) : List[SpotifyPlaylist] = {
  def get_playlists(token : String, user : String) : Unit = {
    val artistUri = uri"$apiEndpoint/v1/users/$user/playlists"
    
    implicit val backend = HttpURLConnectionBackend()
    val request = sttp.auth.bearer(token).get(artistUri)
    val response = request.send()

    val data = response.body match {
      case Left(x) => Json.parse(x)
      case Right(x) => Json.parse(x)
    }

    Console.println(data)
  }

  def get_user(token : String) : Unit = {
    val artistString = apiEndpoint + "/v1/users/fishehh"
    val artistUri = uri"$artistString"
    
    implicit val backend = HttpURLConnectionBackend()
    val request = sttp.auth.bearer(token).get(artistUri)
    val response = request.send()

    val data = response.body match {
      case Left(x) => Json.parse(x)
      case Right(x) => Json.parse(x)
    }
    Console.println(data)
  }
}

object SpotifyTest extends App {
    // Get a token
    val response = SpotifyUtility.tokenize()
    val token = (response \ "access_token").asOpt[String] match {
      case None => "No token"
      case Some(s) => s
    }
    Console.println(token)
    // Get a token: FINISHED

    // val token = "BQDgtI25-oL2PApN4w-3UA9XsvZPG56dKKmuzfWjLRTnv3Zi1hkBHE9GG-nEoAGbd2MDYvzBlODiekGj2xohoc7OMG_MqxSU5fAmWJyDjI_MjH1gGNqBr_wVQ4oMLTkul0BPDRVsRjpar_AHucXz1I1tcKeHCmI"
    SpotifyUtility.get_playlists(token, "fishehh") 

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

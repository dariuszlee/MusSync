package db

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import java.sql.DriverManager
import java.sql.Connection
import org.postgresql.util.PSQLException

import java.sql.Timestamp
import java.util.Calendar

import db._
import services.spotify.SpotifyAlbumActor.AlbumInfo


object SpotifyDbActor {
  trait AlbumClass
  object AlbumTag {
    case object New extends AlbumClass
    case object Seen extends AlbumClass
    case object Disliked extends AlbumClass
    case object Liked extends AlbumClass
    case object Old extends AlbumClass
  }
    
  case class PSQLExceptionWrapper(ex: PSQLException)

  case class InsertMusSyncUser(mus_id: String, mus_user_id: String, password: String)
  case class InsertSpotifyUser(mus_id: String, spotify_id: String, refresh_token: String)

  // Album cases
  case class InsertSpotifyAlbum(mus_id: String, spotify_id: String, spotify_album_id: String, tag: AlbumClass)
  case class InsertSpotifyAlbumInfo(album_info: AlbumInfo, tag: AlbumClass)
  case class CheckAlbumExistence(ablum_info: AlbumInfo)
  case class AlbumExists(ablum_info: AlbumInfo)

  // Artist Cases
  case class InsertSpotifyArtist(mus_id: String, spotify_id: String, spotify_artist_id: String)

  def props(host: String) = Props(new SpotifyDbActor(host))
}

class SpotifyDbActor(host: String) extends Actor with akka.actor.ActorLogging {
  import SpotifyDbActor._

  val driver : String = "org.postgresql.Driver"
  val username = "dariuslee"
  val url = s"jdbc:postgresql://$host/mus_sync_test"
  println(url)

  val mus_sync_db_params = "id, login, password_hash"
  val spotify_user_db_params = "spotify_id, id, refresh_token"
  val spotify_artist_db_params = "id, spotify_id, spotify_artist_id, date_added"
  val spotify_album_db_params = "id, spotify_id, spotify_album_id, date_added, tag"

  val connection : Connection = DriverManager.getConnection(url, username, "ma456tilda")

  val calendar = Calendar.getInstance()

  var unique_ids = Set[String]()

  def receive = {
    case InsertMusSyncUser(mus_id, mus_user_id, password) => {
      val query_str = s"INSERT INTO mus_sync_user($mus_sync_db_params) VALUES('$mus_id', '$mus_user_id', '$password')"
      log.info(s"Executing: $query_str")
      val prepared = connection.prepareStatement(query_str)
      try {
        prepared.executeUpdate()
      }
      catch {
        case psqlEx : PSQLException => {
          log.error(s"ERROR: $psqlEx")
        }
        case _ : Throwable => {}
      }
      prepared.close()
    }
    case InsertSpotifyUser(mus_id, spotify_id, refresh_token) => {
      val query_str = s"INSERT INTO spotify_user($spotify_user_db_params) VALUES('$spotify_id', '$mus_id', '$refresh_token')"
      log.debug(s"Executing: $query_str")
      val prepared = connection.prepareStatement(query_str)
      try {
        prepared.executeUpdate()
      }
      catch {
        case psqlEx : PSQLException => {
          log.error(s"ERROR: $psqlEx")
        }
        case _ : Throwable => {}
      }
      prepared.close()
    }
    case InsertSpotifyAlbum(mus_id, spotify_id, album_id, tag) => {
      val time = new Timestamp(calendar.getTime().getTime())
      val query_str = s"INSERT INTO spotify_album($spotify_album_db_params) VALUES('$mus_id', '$spotify_id', '$album_id', '$time','$tag')"
      log.info(s"Executing InsertSpotifyAlbum: $query_str")
      val prepared = connection.prepareStatement(query_str)
      try {
        prepared.executeUpdate()
      }
      catch {
        case psqlEx : PSQLException => {}
        {
          log.error(s"Error $psqlEx")
        }
        case _ : Throwable => {}
      }
      prepared.close()
    }
    case InsertSpotifyAlbumInfo(album_info, album_tag) => {
      self ! InsertSpotifyAlbum(album_info.mus_id, album_info.spotify_id, album_info.spotify_album_id, album_tag)
    }
    case CheckAlbumExistence(album_info) => {
      val spot_user_id = album_info.spotify_id
      val mus_sync_user = album_info.mus_id
      val spotify_album_id = album_info.spotify_album_id
      val query_str = s"SELECT * FROM spotify_album WHERE spotify_id='$spot_user_id' AND id='$mus_sync_user' AND spotify_album_id='$spotify_album_id'"
      log.info(s"Executing Check Album Existence: $query_str")
      val prepared = connection.prepareStatement(query_str)
      try {
        val does_item_exist = prepared.executeQuery().next()
        if(!does_item_exist){
          self ! InsertSpotifyAlbumInfo(album_info, AlbumTag.New)
        }
        else {
          sender() ! AlbumExists(album_info)
        }
      }
      catch {
        case psqlEx : PSQLException => {}
        {
          log.error(s"Error $psqlEx")
        }
        case _ : Throwable => {}
      }
      finally {
        prepared.close()
      }
       
    }
    case InsertSpotifyArtist(mus_sync_user, spot_user_id, spotify_artist_id) => {
      val time = new Timestamp(calendar.getTime().getTime())
      val query_str = s"INSERT INTO spotify_artist($spotify_artist_db_params) VALUES('$mus_sync_user', '$spot_user_id', '$spotify_artist_id', '$time')"
      log.debug(s"Executing: $query_str")
      val prepared = connection.prepareStatement(query_str)
      try {
        prepared.executeUpdate()
      }
      catch {
        case psqlEx : PSQLException => {
          log.error(s"Error $psqlEx")
        }
        case _ : Throwable => {}
      }
      prepared.close()
    }
  }
}

object SpotifyDbTests extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val dbActor = system.actorOf(SpotifyDbActor.props("localhost"), "spotifyDb")

  dbActor ! SpotifyDbActor.InsertSpotifyArtist("asdf", "asdf", "asdf")
  // dbActor ! SpotifyDbActor.CheckAlbumExistence("mus_sync_user", "spot_user_id", "6pwdy6oQdwSQo8XOfpfAJJ")
  // dbActor ! SpotifyDbActor.CheckAlbumExistence("mus_sync_user", "spot_user_id", "pwdy6oQdwSQo8XOfpfAJJ")

  readLine()
  system.terminate()
}

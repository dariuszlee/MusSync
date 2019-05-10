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
  case class InsertSpotifyAlbum(mus_id: String, spotify_id: String, spotify_album_id: String, tag: AlbumClass)
  case class InsertSpotifyArtist(mus_id: String, spotify_id: String, spotify_artist_id: String)


  def props = Props(new SpotifyDbActor)
}

class SpotifyDbActor extends Actor with akka.actor.ActorLogging {
  import SpotifyDbActor._

  val driver : String = "org.postgresql.Driver"
  val username = "dariuslee"
  val url = "jdbc:postgresql:mus_sync_test"

  val mus_sync_db_params = "id, login, password_hash"
  val spotify_user_db_params = "spotify_id, id, refresh_token"
  val spotify_artist_db_params = "id, spotify_id, spotify_artist_id, date_added"
  val spotify_album_db_params = "id, spotify_id, spotify_album_id, date_added, tag"

  val connection : Connection = DriverManager.getConnection(url, username, "test")

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

  val dbActor = system.actorOf(SpotifyDbActor.props, "spotifyDb")
  val test_actor = system.actorOf(SpotifyDbActor.props, "test_actor")

  dbActor ! SpotifyDbActor.InsertSpotifyArtist("asdf", "asdf", "asdf")

  readLine()
  system.terminate()
}

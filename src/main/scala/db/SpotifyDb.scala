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

object AlbumTag extends Enumeration {
  type AlbumTag = Value
  val New, Seen, Disliked, Liked, Old = Value
}
import AlbumTag._

object SpotifyDbActor {
  case class DumpAlbums(user_id: String, album_ids : Seq[String])
  case class InsertSpotifyItem(id: String)
  case class PSQLExceptionWrapper(ex: PSQLException)
  case object GetUnique
  case class CheckIfCurrent(art_id: String, alb_id: String, from: ActorRef, respond_with: Boolean => Object)
  case class InsertArtist(spotify_id: String, spotify_artist_id: String)

  case class InsertSpotifyUser()
  case class InsertSpotifyAlbum(mus_id: String, spotify_id: String, spotify_artist_id: String, tag: AlbumTag)
  case class InsertSpotifyArtist(mus_id: String, spotify_id: String, spotify_artist_id: String)

  case object CreateDbs

  def props = Props(new SpotifyDbActor)
}

class SpotifyDbActor extends Actor with akka.actor.ActorLogging {
  import SpotifyDbActor._

  val driver : String = "org.postgresql.Driver"
  val username = "dariuslee"
  val url = "jdbc:postgresql:mus_sync_test"
  val spotify_artist_db_params = "mus_sync_user_id, spotify_artist_id, when_added"
  val connection : Connection = DriverManager.getConnection(url, username, "ma456tilda")

  val calendar = Calendar.getInstance()

  var unique_ids = Set[String]()

  def receive = {
    case InsertArtist(mus_sync_user, spotify_artist_id) => {
      val time = new Timestamp(calendar.getTime().getTime())
      val query_str = s"INSERT INTO spotify_artists($spotify_artist_db_params) VALUES('$mus_sync_user', '$spotify_artist_id', ?)"
      log.info(s"Executing: $query_str")
      val prepared = connection.prepareStatement(query_str)
      prepared.setTimestamp(1, time)
      try {
        prepared.executeUpdate()
      }
      catch {
        case psqlEx : PSQLException => {}
        case _ => {}
      }
      prepared.close()
    }
    case DumpAlbums(user_id, albums) => {
      var value_string = ""
      for(album <- albums) {
        value_string += s"('$user_id', '$album', false),"
      }
      value_string = value_string.dropRight(1)

      var query_string = s"INSERT INTO spotify_albums(user_id, album_id, is_checked) VALUES $value_string"

      val statement = connection.createStatement()
      val result_set = statement.executeUpdate(query_string)
    }
    case InsertSpotifyItem(new_id) => {
      val statement = connection.createStatement()
      val result_set = statement.executeQuery(s"INSERT INTO spotify_artists()")
      result_set.next()
      val data = result_set.getString("asdf")
    }
    case GetUnique => {
      val statement = connection.createStatement()
      val result_set = statement.executeQuery("SELECT * FROM spotify_artists")
      result_set.next()
      val data = result_set.getString("asdf")
      println("DATA IS:", data)
    }
    case CheckIfCurrent(artist_id, latest_album_id, respond_to, respond_with) => {
      val someting = false
      respond_to ! respond_with(someting)
    }
    case CreateDbs => {
      val create_str = s"CREATE TABLE spotify_artist(mus_sync_user_id VARCHAR, spotify_artist_id VARCHAR, when_added TIMESTAMP)"

      val statement = connection.createStatement()
      val result_set = statement.executeQuery(create_str)
    }
  }
}

object SpotifyDbTests extends App {
  object TestActor{
    case class MyCurrentCheck(v: Boolean)
  }
  class TestActor extends Actor {
    def receive = {
      case TestActor.MyCurrentCheck(yes_or_no) => println("Answer is: ", yes_or_no)
    }
  }

  import SpotifyDbActor._
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val dbActor = system.actorOf(Props(new SpotifyDbActor()), "spotifyDb")
  val test_actor = system.actorOf(Props(new TestActor()), "test_actor")

  // dbActor ! CheckIfCurrent("asdf", "asdf", test_actor, TestActor.MyCurrentCheck)
  dbActor ! CreateDbs

  readLine()
  system.terminate()
}

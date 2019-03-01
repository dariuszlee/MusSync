import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import java.sql.DriverManager
import java.sql.Connection

import java.sql.Timestamp
import java.util.Calendar

object SpotifyDbActor {
  case class InsertSpotifyItem(url: String, new_id: String, is_dump: Boolean)
  case object GetUnique
  case class CheckIfCurrent(art_id: String, alb_id: String, from: ActorRef, respond_with: Boolean => Object)
  case class InsertArtist(mus_sync_user: String, spotify_artist_id: String)

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
      prepared.executeUpdate()
      prepared.close()
    }
    case InsertSpotifyItem(url, new_id, is_dump) => {
      unique_ids += url  

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

  dbActor ! CheckIfCurrent("asdf", "asdf", test_actor, TestActor.MyCurrentCheck)

  readLine()
  system.terminate()
}

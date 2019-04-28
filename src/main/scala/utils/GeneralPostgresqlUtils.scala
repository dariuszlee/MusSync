import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import java.sql.DriverManager
import java.sql.Connection

object PostgresUtils {
  def props : Props = Props(new PostgresUtils())

  case object InitializeEmpty
  case object RemoveDbs
}

class PostgresUtils extends Actor with akka.actor.ActorLogging {
  import PostgresUtils._

  val username = "dariuslee"
  val url = "jdbc:postgresql:mus_sync_test"
  val connection : Connection = DriverManager.getConnection(url, username, "ma456tilda")

  override def receive = {
    case InitializeEmpty => {
      val statement = connection.createStatement()

      val create_user_table = "CREATE TABLE IF NOT EXISTS mus_sync_user(id varchar UNIQUE NOT NULL, login varchar UNIQUE NOT NULL, password_hash varchar UNIQUE NOT NULL)"
      val create_spotify_account = "CREATE TABLE IF NOT EXISTS spotify_user(spotify_id VARCHAR UNIQUE NOT NULL, id varchar REFERENCES mus_sync_user(id), refresh_token VARCHAR)"
      val create_spotify_artist = "CREATE TABLE IF NOT EXISTS spotify_artist(id varchar REFERENCES mus_sync_user(id), spotify_id VARCHAR REFERENCES spotify_user(spotify_id), spotify_artist_id VARCHAR, date_added TIMESTAMP)"
      val create_spotify_album = "CREATE TABLE IF NOT EXISTS spotify_album(spotify_id VARCHAR REFERENCES spotify_user(spotify_id) NOT NULL, id varchar REFERENCES mus_sync_user(id), spotify_album_id VARCHAR NOT NULL, date_added TIMESTAMP NOT NULL, tag VARCHAR NOT NULL)"

      statement.execute(create_user_table)
      statement.execute(create_spotify_account)
      statement.execute(create_spotify_artist)
      statement.execute(create_spotify_album)
      statement.close()
      log.info("Created tables: create_user_table, create_spotify_account, spotify_artist, spotify_album")
    }
    case RemoveDbs => {
      val drop_spotify_artist = "DROP TABLE IF EXISTS spotify_artist"
      val drop_spotify_album = "DROP TABLE IF EXISTS spotify_album"
      val drop_user_table = "DROP TABLE IF EXISTS mus_sync_user CASCADE"
      val drop_spotify_account= "DROP TABLE IF EXISTS spotify_user CASCADE"      

      val statement = connection.createStatement()
      statement.execute(drop_spotify_artist)
      statement.execute(drop_spotify_album)
      statement.execute(drop_spotify_account)
      statement.execute(drop_user_table)
      statement.close()
    }
  }
}

object PostgresqlTest extends App {
  implicit val context = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.dispatcher

  import PostgresUtils._

  val postgres = context.actorOf(PostgresUtils.props, "postgres") 
  postgres ! RemoveDbs 
  postgres ! InitializeEmpty

  readLine()
  context.terminate()
}

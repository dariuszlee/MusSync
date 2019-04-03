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

      statement.execute(create_user_table)
      statement.execute(create_spotify_account)
      statement.close()
      log.info("Created tables: create_user_table, create_spotify_account")
    }
  }
}

object PostgresqlTest extends App {
  implicit val context = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.dispatcher

  import PostgresUtils._

  val postgres = context.actorOf(PostgresUtils.props, "postgres") 
  postgres ! InitializeEmpty

  readLine()
  context.terminate()
}

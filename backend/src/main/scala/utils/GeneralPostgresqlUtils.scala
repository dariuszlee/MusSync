import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import java.sql.DriverManager
import java.sql.Connection

import org.apache.commons.cli._

object PostgresUtils {
  def props(host: String) : Props = Props(new PostgresUtils(host))

  case object InitializeEmpty
  case object RemoveDbs
}

class PostgresUtils(host: String) extends Actor with akka.actor.ActorLogging {
  import PostgresUtils._

  val username = "dariuslee"
  val url = s"jdbc:postgresql://$host/mus_sync_test"
  val connection : Connection = DriverManager.getConnection(url, username, "ma456tilda")

  override def receive = {
    case InitializeEmpty => {
      val statement = connection.createStatement()

      val create_user_table = """
      CREATE TABLE IF NOT EXISTS mus_sync_user(
        id varchar UNIQUE NOT NULL, 
        login varchar UNIQUE NOT NULL, 
        password_hash varchar UNIQUE NOT NULL
      )"""

      val create_spotify_account = """
      CREATE TABLE IF NOT EXISTS spotify_user(
        spotify_id VARCHAR UNIQUE NOT NULL, 
        id varchar REFERENCES mus_sync_user(id), 
        refresh_token VARCHAR)"""

      val create_spotify_artist = """
      CREATE TABLE IF NOT EXISTS spotify_artist(
        id varchar REFERENCES mus_sync_user(id) NOT NULL, 
        spotify_id VARCHAR REFERENCES spotify_user(spotify_id) NOT NULL, 
        spotify_artist_id VARCHAR NOT NULL, 
        date_added TIMESTAMP, 
        PRIMARY KEY (id, spotify_id, spotify_artist_id))"""

      val create_spotify_album = """
      CREATE TABLE IF NOT EXISTS spotify_album(
        spotify_id VARCHAR REFERENCES spotify_user(spotify_id) NOT NULL, 
        id varchar REFERENCES mus_sync_user(id), 
        spotify_album_id VARCHAR NOT NULL, 
        date_added TIMESTAMP NOT NULL, 
        tag VARCHAR NOT NULL, 
        PRIMARY KEY (id, spotify_id, spotify_album_id)
      )"""

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
  def parse_args(args: Array[String]) : CommandLine = {
    val options : Options = new Options()
    options.addOption(new Option("h", "db_host", true, "Db_host"))
    val parser : CommandLineParser = new DefaultParser()
    val cmd : CommandLine = parser.parse(options, args)
    
    return cmd
  } 

  override def main(args: Array[String]) = {
    var arg_map = parse_args(args)
    arg_map.getOptions().foreach(x => println(x))

    implicit val context = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = context.dispatcher

    import PostgresUtils._

    println(arg_map.getOptionValue("h", "localhost"))
    val postgres = context.actorOf(PostgresUtils.props(arg_map.getOptionValue("db_host", "localhost")), "postgres") 
    postgres ! RemoveDbs 
    println("Removed. Recreate dbs....")
    postgres ! InitializeEmpty

    readLine()
    context.terminate()
  }
}

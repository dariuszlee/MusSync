package services.spotify

import org.apache.commons.cli._
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Level

import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

import akka.routing.SmallestMailboxPool

import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy._
import scala.concurrent.duration._

import scala.collection.mutable.HashMap

import db.SpotifyDbActor._

object SpotifyReleaseActor {
  def props(is_debug: Boolean, mus_sync_user: String, spot_user_id: String) : Props = Props(new SpotifyReleaseActor(is_debug, mus_sync_user, spot_user_id))
  def initial_url : String = "https://api.spotify.com/v1/me/following?type=artist&limit=20"

  case object StartJob
  case object CheckJob
  case class FinishedUrl(url: String, num_indiv_art: Integer)
  case class AddUrl(url: String)
  class EndJob extends Exception
}

class SpotifyReleaseActor(is_debug: Boolean, mus_sync_user: String, spot_user_id: String) extends Actor with akka.actor.ActorLogging {
  import SpotifyReleaseActor._
  import SpotifyArtistActor._
  import context._

  implicit var mat = ActorMaterializer()(context.system)
  var completed : Set[String] = Set[String]()
  var completed_actors : HashMap[String, ActorRef] = new HashMap[String, ActorRef]()
  var request_actor = context.actorOf(SpotifyRequestActor.props, "req_actor_root")

  val db_actor = context.actorSelection("/user/db-actor")

  var total_artists = 0
  var num_indiv_artists = 0

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case ex: EndJob => {
        log.error("Handling error: {}", ex)
        Resume
      }
    }

  import java.security.MessageDigest
  def hash(to_hash : String) = {
    MessageDigest.getInstance("MD5").digest(to_hash.getBytes)
  }

  override def receive = {
    case StartJob => {
      db_actor ! InsertMusSyncUser(mus_sync_user, mus_sync_user, "fake_password")
      val refresh_token = "AQBjrJOtmyOFde6P9qCH_YiSTtURDZErXB-hjzoIv-sn6a7klmF3Kn3DvNGrNM5W91P2moun8QmYEE77jRToBt4OpiePetlb58yiGZRzbZqsz3uqs04P9ljfuXMrcwqrZ2XV7w"
      db_actor ! InsertSpotifyUser(mus_sync_user, spot_user_id, refresh_token)
      self ! AddUrl(initial_url)
    }
    case AddUrl(uri: String) => {
      if(!completed.contains(uri)){
        val uri_hash = "artist_act_" + BigInt(hash(uri))
        val get_artists_actor = context.actorOf(SpotifyArtistActor.props(uri, self, is_debug, mus_sync_user, spot_user_id), uri_hash)
        completed = completed + uri
        completed_actors.put(uri, get_artists_actor)
        get_artists_actor ! HandleJobStart(uri)

        total_artists += 1
      }
    }
    case CheckJob => {
      if(!completed_actors.isEmpty){
        completed.foreach(x => {
          log.info("Sending check to: {}", x)
          completed_actors(x) ! SpotifyArtistActor.CheckStatus
        })
      }
      else {
        log.info("Finishing Application - Stats:")
        log.info("Total Artists: {}", total_artists)
        log.info("Number Individual Artists: {}", num_indiv_artists)
        context.stop(self) 
      }
    }
    case FinishedUrl(url, num_indiv_art) => {
      log.info("Finished URL: {}", url)
      completed = completed - url
      context.stop(completed_actors(url))
      completed_actors.remove(url)

      num_indiv_artists += num_indiv_art
    }
  }
}

object ReleaseApp extends App {

  def parse_args(args: Array[String]) : CommandLine = {
    val options : Options = new Options()
    options.addOption(new Option("d", "debug", false, "Turn on debug."))
    options.addOption(new Option("h", "db_host", true, "Db host"))
    val parser : CommandLineParser = new DefaultParser()
    val cmd : CommandLine = parser.parse(options, args)
    
    return cmd
  } 

  override def main(args: Array[String]) = {
    @transient lazy val log = LogManager.getLogger(getClass.getName)
    val arg_map = parse_args(args)

    import db.SpotifyDbActor
    import db.SpotifyDbActor._

    implicit val context = ActorSystem()
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = context.dispatcher

    val mus_sync_user = "mus_sync_user"
    val spot_user_id = "spot_user_id"

    // Infra actors
    val db_actor = context.actorOf(SpotifyDbActor.props(arg_map.getOptionValue("db_host", "localhost")), "db-actor")
    val requestActors = context.actorOf(SmallestMailboxPool(5).props(SpotifyRequestActor.props(materializer)), "req_actor")


    var release_app = if(arg_map.hasOption("d")){
      log.error("Application in dump mode...")
      context.actorOf(SpotifyReleaseActor.props(true, mus_sync_user, spot_user_id), "release-actor")
    }
    else {
      log.error("Application in normal mode...")
      context.actorOf(SpotifyReleaseActor.props(false, mus_sync_user, spot_user_id), "release-actor")
    }

    release_app ! SpotifyReleaseActor.StartJob

    context.scheduler.schedule(10000 milliseconds, 3000 milliseconds, release_app, SpotifyReleaseActor.CheckJob)

    // Halt execution
    readLine()
    // Clean-up here
    context.terminate()
  }
}

package services.spotify

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

object ReleaseActor {
  def props(is_debug: Boolean) : Props = Props(new ReleaseActor(is_debug))
  def initial_url : String = "https://api.spotify.com/v1/me/following?type=artist&limit=20"

  case object StartJob
  case object CheckJob
  case class FinishedUrl(url: String, num_indiv_art: Integer)
  case class AddUrl(url: String)
  class EndJob extends Exception
}

class ReleaseActor(is_debug: Boolean) extends Actor with akka.actor.ActorLogging {
  import ReleaseActor._
  import ArtistActor._
  import context._

  implicit var mat = ActorMaterializer()(context.system)
  var completed : Set[String] = Set[String]()
  var completed_actors : HashMap[String, ActorRef] = new HashMap[String, ActorRef]()
  var request_actor = context.actorOf(SpotifyRequestActor.props, "req_actor_root")

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
      self ! AddUrl(initial_url)
    }
    case AddUrl(uri: String) => {
      if(!completed.contains(uri)){
        val uri_hash = "artist_act_" + BigInt(hash(uri))
        val get_artists_actor = context.actorOf(ArtistActor.props(uri, self, is_debug), uri_hash)
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
          completed_actors(x) ! ArtistActor.CheckStatus
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
  implicit val context = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = context.dispatcher

  val requestActors = context.actorOf(SmallestMailboxPool(5).props(SpotifyRequestActor.props(materializer)), "req_actor")
  val release_app = context.actorOf(ReleaseActor.props(true), "release-actor")
  val db_actor = context.actorOf(SpotifyDbActor.props, "db-actor")

  release_app ! ReleaseActor.StartJob

  context.scheduler.schedule(3000 milliseconds, 3000 milliseconds, release_app, ReleaseActor.CheckJob)

  // Halt execution
  readLine()
  // Clean-up here
  import SpotifyDbActor._
  db_actor ! GetUnique
  context.terminate()
}

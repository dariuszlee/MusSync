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

final case class SpotifyRelease(name : String)

object SpotifyTest extends App {
    implicit val system = ActorSystem("Spotify-Api-Test")
    implicit val materializer = ActorMaterializer()

    val sourceData = SpotifyRelease("SomeGuy") :: SpotifyRelease("SomeOtherGuy") :: Nil

    val source : Source[SpotifyRelease, NotUsed] = Source(sourceData)
    val sink = Sink.foreach(( x : SpotifyRelease) => {
      Console.println(x.name)
    })

    val runnable = source.toMat(sink)(Keep.right)
    val output = runnable.run()
}

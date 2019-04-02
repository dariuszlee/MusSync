import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Actor
import akka.actor.Props
import akka.stream.ActorMaterializer

object PostgresUtils {
  def props : Props = Props(new PostgresUtils())

  case object InitializeEmpty
}

class PostgresUtils extends Actor with akka.actor.ActorLogging {
  import PostgresUtils._

  override def receive = {
    case InitializeEmpty => {}
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

package shopping.cart

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.{ ServerReflection, ServiceHandler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import shopping.cart.proto.{ ShoppingCartService, ShoppingCartServiceHandler }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

/*
 * @created - 14/02/2021
 * @project - shopping-cart-service
 * @author  - Michael Mustapha
 */
object ShoppingCartServer {

  def start(
      interface: String,
      port: Int,
      system: ActorSystem[_],
      grpcService: ShoppingCartService): Unit = {
    implicit val sys: ActorSystem[_] = system
    implicit val ec: ExecutionContext = system.executionContext

    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(
        ShoppingCartServiceHandler.partial(grpcService),
        // ServerReflection enabled to support grpcurl without import-path and proto parameters
        ServerReflection.partial(List(ShoppingCartService)))

    val bound = Http()
      .newServerAt(interface, port)
      .bind(service)
      .map(_.addToCoordinatedShutdown(3.seconds))

    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(
          "Shopping online at gRPC server {}:{}",
          address.getHostString,
          address.getPort)
      case Failure(exception) =>
        system.log
          .error("Failed to bind gRPC endpoint, terminating system", exception)
        system.terminate()
    }
  }
}

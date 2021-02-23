package shopping.cart
import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import io.grpc.Status
import org.slf4j.LoggerFactory
import shopping.cart.ShoppingCart.Summary
import shopping.cart.proto.{
  AddItemRequest,
  Cart,
  CheckoutRequest,
  GetCartRequest,
  ShoppingCartService
}

import scala.concurrent.{ Future, TimeoutException }

/*
 * @created - 14/02/2021
 * @project - shopping-cart-service
 * @author  - Michael Mustapha
 */
class ShoppingCartServiceImpl(system: ActorSystem[_])
    extends ShoppingCartService {
  import system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout = Timeout.create(
    system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)

  override def addItem(in: AddItemRequest): Future[Cart] = {
    logger.info("addItem {} to cart {}", in.itemId, in.cartId)
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply: Future[ShoppingCart.Summary] =
      entityRef.askWithStatus(ShoppingCart.AddItems(in.itemId, in.quantity, _))
    //    Future.successful(Cart(items = List(Item(in.itemId, in.quantity))))
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  override def checkout(in: CheckoutRequest): Future[Cart] = {
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val reply: Future[Summary] = entityRef.askWithStatus(ShoppingCart.Checkout)
    val response = reply.map(cart => toProtoCart(cart))
    convertError(response)
  }

  override def getCart(in: GetCartRequest): Future[Cart] = {
    val entityRef = sharding.entityRefFor(ShoppingCart.EntityKey, in.cartId)
    val response = entityRef.ask(ShoppingCart.Get).map { cart =>
      if (cart.items.isEmpty)
        throw new GrpcServiceException(
          Status.NOT_FOUND.withDescription(s"Cart ${in.cartId} not found"))
      else toProtoCart(cart)
    }
    convertError(response)
  }

  private def toProtoCart(cart: ShoppingCart.Summary): Cart = {
    Cart(
      cart.items.iterator.map { case (itemId, quantity) =>
        proto.Item(itemId, quantity)
      }.toSeq,
      cart.checkout)
  }

  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(
            Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(
          new GrpcServiceException(
            Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
  }
}

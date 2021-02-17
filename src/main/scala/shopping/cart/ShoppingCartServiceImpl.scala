package shopping.cart
import akka.actor.typed.ActorSystem
import org.slf4j.LoggerFactory
import shopping.cart.proto.{ AddItemRequest, Cart, Item, ShoppingCartService }

import scala.concurrent.Future

/*
 * @created - 14/02/2021
 * @project - shopping-cart-service
 * @author  - Michael Mustapha
 */
class ShoppingCartServiceImpl(system: ActorSystem[_])
    extends ShoppingCartService {

  private val logger = LoggerFactory.getLogger(getClass)

  override def addItem(in: AddItemRequest): Future[Cart] = {
    logger.info("addItem {} to cart {}", in.itemId, in.cartId)
    Future.successful(Cart(items = List(Item(in.itemId, in.quantity))))
  }
}

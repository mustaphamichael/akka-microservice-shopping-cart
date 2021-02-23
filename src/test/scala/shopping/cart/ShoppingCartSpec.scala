package shopping.cart

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.pattern.StatusReply
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import shopping.cart.ShoppingCart.{ AddItems, Checkout, ItemAdded, Summary }

/*
 * @created - 17/02/2021
 * @project - shopping-cart-service
 * @author  - Michael Mustapha
 */
object ShoppingCartSpec {
  val config = ConfigFactory
    .parseString("""
      |akka.actor.serialization-bindings {
      |  "shopping.cart.CborSerializable" = jackson-cbor
      |}
      |""".stripMargin)
    .withFallback(EventSourcedBehaviorTestKit.config)
}

class ShoppingCartSpec
    extends ScalaTestWithActorTestKit(ShoppingCartSpec.config)
    with AnyWordSpecLike
    with BeforeAndAfterEach {

  private val cartId = "testCart"
  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[
      ShoppingCart.Command,
      ShoppingCart.Event,
      ShoppingCart.State](system, ShoppingCart(cartId))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "The Shopping Cart" should {

    "add item" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](replyTo =>
          ShoppingCart.AddItems("foo", 42, replyTo))
      result1.reply should ===(
        StatusReply.Success(Summary(Map("foo" -> 42), false)))
      result1.event should ===(ItemAdded(cartId, "foo", 42))
    }

    "reject already added item" in {
      val result1 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](
          AddItems("foo", 42, _))
      result1.reply.isSuccess should ===(true)
      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](
          AddItems("foo", 13, _))
      result2.reply.isError should ===(true)
    }

    "checkout" in {
      val result1 = eventSourcedTestKit.runCommand[StatusReply[Summary]](
        AddItems("foo", 42, _))
      result1.reply.isSuccess should ===(true)
      val result2 =
        eventSourcedTestKit.runCommand[StatusReply[Summary]](Checkout)
      result2.reply should ===(
        StatusReply.Success(Summary(Map("foo" -> 42), checkout = true)))
      val result3 = eventSourcedTestKit.runCommand[StatusReply[Summary]](
        AddItems("bar", 13, _))
      result3.reply.isSuccess should ===(false)
    }

    "get" in {
      val result1 = eventSourcedTestKit.runCommand[StatusReply[Summary]](
        AddItems("foo", 42, _))
      result1.reply.isSuccess should ===(true)

      val result2 =
        eventSourcedTestKit.runCommand[Summary](ShoppingCart.Get)
      result2.reply should ===(Summary(Map("foo" -> 42), checkout = false))
    }
  }
}

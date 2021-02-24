package shopping.cart

import akka.actor.typed.{ ActorRef, ActorSystem, Behavior, SupervisorStrategy }
import akka.cluster.sharding.typed.scaladsl.{
  ClusterSharding,
  Entity,
  EntityContext,
  EntityTypeKey
}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{
  Effect,
  EventSourcedBehavior,
  ReplyEffect,
  RetentionCriteria
}

import java.time.Instant
import scala.concurrent.duration._

/*
 * @created - 17/02/2021
 * @project - shopping-cart-service
 * @author  - Michael Mustapha
 */
object ShoppingCart {

  sealed trait Command extends CborSerializable

  final case class AddItems(
      itemId: String,
      quantity: Int,
      replyTo: ActorRef[StatusReply[Summary]])
      extends Command

  final case class Checkout(replyTo: ActorRef[StatusReply[Summary]])
      extends Command

  final case class Get(replyTo: ActorRef[Summary]) extends Command

  /** Summary of the shopping cart state, used in reply messages. */
  final case class Summary(items: Map[String, Int], checkout: Boolean)
      extends CborSerializable

  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  final case class ItemAdded(cartId: String, itemId: String, quantity: Int)
      extends Event

  final case class CheckedOut(cartId: String, eventTime: Instant) extends Event

  final case class State(items: Map[String, Int], checkoutDate: Option[Instant])
      extends CborSerializable {
    def isCheckedOut: Boolean = checkoutDate.isDefined

    def checkout(now: Instant): State = copy(checkoutDate = Some(now))

    def toSummary: Summary = Summary(items, isCheckedOut)

    def hasItem(itemId: String): Boolean = items.contains(itemId)

    def isEmpty: Boolean = items.isEmpty

    def updateItem(itemId: String, quantity: Int): State = {
      quantity match {
        case 0 => copy(items = items - itemId)
        case _ => copy(items = items + (itemId -> quantity))
      }
    }
  }

  object State {
    val empty: State = State(items = Map.empty, checkoutDate = None)
  }

  val tags = Vector.tabulate(5)(i => s"carts-$i")
  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")

  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[Command] => Behavior[Command] = {
      entityContext =>
        val i = math.abs(entityContext.entityId.hashCode % tags.size)
        val selectedTag = tags(i)
        ShoppingCart(entityContext.entityId, selectedTag)
    }

    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }

  def apply(cartId: String, projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, cartId),
        emptyState = State.empty,
        commandHandler =
          (state, command) => handleCommand(cartId, state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  private def handleCommand(
      cartId: String,
      state: State,
      command: Command): ReplyEffect[Event, State] = {
    if (state.isCheckedOut) checkedOutShoppingCart(state, command)
    else openShoppingCart(cartId, state, command)
  }

  private def openShoppingCart(
      cartId: String,
      state: State,
      command: Command): ReplyEffect[Event, State] = {
    command match {
      case AddItems(itemId, quantity, replyTo) =>
        if (state.hasItem(itemId))
          Effect.reply(replyTo)(
            StatusReply.Error(
              s"Item '$itemId' was already added to this shopping cart"))
        else if (quantity <= 0)
          Effect.reply(replyTo)(
            StatusReply.Error("Quantity must be greater than zero"))
        else
          Effect
            .persist(ItemAdded(cartId, itemId, quantity))
            .thenReply(replyTo) { updatedCart =>
              StatusReply.Success(updatedCart.toSummary)
            }
      case Checkout(replyTo) =>
        if (state.isEmpty)
          Effect.reply(replyTo)(
            StatusReply.Error("Cannot checkout an empty shopping cart"))
        else
          Effect
            .persist(CheckedOut(cartId, Instant.now()))
            .thenReply(replyTo)(updatedCart =>
              StatusReply.Success(updatedCart.toSummary))
      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)
    }
  }

  private def checkedOutShoppingCart(
      state: State,
      command: Command): ReplyEffect[Event, State] = {
    command match {
      case cmd: AddItems =>
        Effect.reply(cmd.replyTo)(
          StatusReply.Error(
            "Can't add an item to an already checked out shopping cart"))
      case Checkout(replyTo) =>
        Effect.reply(replyTo)(
          StatusReply.Error(
            "Can't checkout an already checked out shopping cart"))
      case Get(replyTo) =>
        Effect.reply(replyTo)(state.toSummary)
    }
  }

  private def handleEvent(state: State, event: Event) = {
    event match {
      case ItemAdded(_, itemId, quantity) =>
        state.updateItem(itemId, quantity)
      case CheckedOut(_, eventTime) =>
        state.checkout(eventTime)
    }
  }
}

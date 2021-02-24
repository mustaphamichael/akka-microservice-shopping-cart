package shopping.cart

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.{ ProjectionBehavior, ProjectionId }
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ ExactlyOnceProjection, SourceProvider }
import shopping.cart.repository.{ ItemPopularityRepository, ScalikeJdbcSession }

/*
 * @created - 24/02/2021
 * @project - shopping-cart-service
 * @author  - Michael Mustapha
 */
object ItemPopularityProjection {
  def init(
      system: ActorSystem[_],
      repository: ItemPopularityRepository): Unit = {
    ShardedDaemonProcess(system).init(
      name = "ItemPopularityProjection",
      numberOfInstances = ShoppingCart.tags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system, repository, index)))
    ShardedDaemonProcessSettings(system)
    Some(ProjectionBehavior.Stop)
  }

  private def createProjectionFor(
      system: ActorSystem[_],
      repository: ItemPopularityRepository,
      index: Int)
      : ExactlyOnceProjection[Offset, EventEnvelope[ShoppingCart.Event]] = {
    val tag = ShoppingCart.tags(index)

    val sourceProvider
        : SourceProvider[Offset, EventEnvelope[ShoppingCart.Event]] =
      EventSourcedProvider.eventsByTag(
        system = system,
        readJournalPluginId = JdbcReadJournal.Identifier,
        tag = tag)

    JdbcProjection.exactlyOnce(
      projectionId = ProjectionId("ItemPopularityProjection", tag),
      sourceProvider = sourceProvider,
      handler = () => new ItemPopularityProjectHandler(tag, system, repository),
      sessionFactory = () => new ScalikeJdbcSession())(system)
  }

}

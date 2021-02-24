package shopping.cart.repository

import scalikejdbc.{ scalikejdbcSQLInterpolationImplicitDef, DBSession }

/*
 * @created - 24/02/2021
 * @project - shopping-cart-service
 * @author  - Michael Mustapha
 */
class ItemPopularityRepositoryImpl extends ItemPopularityRepository {
  override def update(
      session: ScalikeJdbcSession,
      itemId: String,
      delta: Int): Unit = {
    session.db.withinTx { implicit dbSession =>
      sql"""
         INSERT INTO item_popularity(itemid, count) VALUES ($itemId, $delta)
         ON CONFLICT (itemid) DO UPDATE SET count = item_popularity.count + $delta
         """.executeUpdate().apply()
    }
  }

  override def getItem(
      session: ScalikeJdbcSession,
      itemId: String): Option[Long] = {
    if (session.db.isTxAlreadyStarted) {
      session.db.withinTx { implicit dbSession => select(itemId) }
    } else {
      session.db.readOnly { implicit dbSession => select(itemId) }
    }
  }

  private def select(itemId: String)(implicit dbSession: DBSession) = {
    sql"SELECT count FROM item_popularity WHERE itemid=$itemId"
      .map(_.long("count"))
      .toOption()
      .apply()
  }
}

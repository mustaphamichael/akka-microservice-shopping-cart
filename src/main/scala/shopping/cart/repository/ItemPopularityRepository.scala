package shopping.cart.repository

/*
 * @created - 24/02/2021
 * @project - shopping-cart-service
 * @author  - Michael Mustapha
 */
trait ItemPopularityRepository {
  def update(session: ScalikeJdbcSession, itemId: String, delta: Int): Unit
  def getItem(session: ScalikeJdbcSession, itemId: String): Option[Long]
}

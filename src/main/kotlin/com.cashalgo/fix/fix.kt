package com.cashalgo.fix

import com.cashalgo.fix.message.*
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.websocket.*
import kotlinx.collections.immutable.immutableHashMapOf
import kotlinx.coroutines.experimental.channels.actor
import org.redisson.client.codec.JsonJacksonMapCodec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import quickfix.*
import quickfix.fix44.Message

val logger: Logger = LoggerFactory.getLogger("FixRouter")

val om = jacksonObjectMapper()
var subs = immutableHashMapOf<String, DefaultWebSocketSession>()
suspend fun broadcast(type: String, obj: Any) {
    val m = om.valueToTree(obj) as ObjectNode
    m.put("type", type)
    val f = Frame.Text(om.writeValueAsString(m))
    for (sub in subs.values) {
        sub.send(f.copy())
    }
}

data class PQ(val price: Double, val quantity: Double) {
    @Suppress("unused")
    constructor(): this(0.0, 0.0)
}

val pq0 = PQ(0.0, 0.0)
data class Exec(var active: Boolean, var status: String, var timestamp: Long, var pq: PQ?, var cumulative: PQ, var amount: Double) {
    @Suppress("unused")
    constructor(): this(false, "", 0L, null, pq0, 0.0)
}

val orders = redis.getMap<String, Order>("oms_orders", JsonJacksonMapCodec(String::class.java, Order::class.java))!!
val trades = redis.getMap<String, Trade>("oms_trades", JsonJacksonMapCodec(String::class.java, Trade::class.java))!!
val positions = redis.getMap<String, Position>("oms_positions", JsonJacksonMapCodec(String::class.java, Position::class.java))!!

data class OrderUpdate(
    val id: String,
    val version: Int,
    val price: Double,
    val quantity: Double)


val oms = actor<OMSMessage> {
    for (message in channel) {
        when (message) {
            is ExecutionReportMessage -> {
                val (exchange, er, data) = message
                val orderId = er.orderId
                val order = orders.getOrPut(orderId, {
                    Order(orderId, exchange, er.symbol, 1, PQ(er.price, er.quantity),
                        Exec(true, "New", er.timestamp, pq0, pq0, 0.0),
                        arrayOf(), null, null)
                })
                if (er.status == "Replaced") {
                    order.version += 1
                    order.prevVersions = order.prevVersions.plus(order.pq)
                    order.pq = PQ(er.price, er.quantity)
                    // TODO: order.pendingAmend
                    val orderUpdate = OrderUpdate(orderId, order.version, order.pq.price, order.pq.quantity)
                    logger.info("{}", orderUpdate)
                }

                val positionId = "${order.exch}|${order.symbol}"
                val position = positions.getOrElse(positionId, { Position(positionId, pq0, 0.0) })
                val exec =
                    if (er.cumExecPrice != null && er.cumExecQuantity != null)
                        Exec(er.active, er.status, er.timestamp,
                            if (er.execPrice != null && er.execQuantity != null) PQ(er.execPrice, er.execQuantity) else null,
                            PQ(er.cumExecPrice, er.cumExecQuantity), er.cumExecPrice*er.cumExecQuantity)
                    else if (er.execPrice != null && er.execQuantity != null) {
                        val amount = order.exec.amount + er.execPrice*er.execQuantity
                        val quantity = order.exec.cumulative.quantity + er.execQuantity
                        Exec(er.active, er.status, er.timestamp,
                            PQ(er.execPrice, er.execQuantity),
                            PQ(amount/quantity, quantity), amount)
                    }
                    else order.exec
                val incrAmount = exec.amount-order.exec.amount
                val incrQuantity = exec.cumulative.quantity-order.exec.cumulative.quantity
                position.add(incrQuantity, incrAmount)

                order.exec = exec
                orders[orderId] = order
                positions[positionId] = position

                logger.info("{}", order)
                broadcast("order", order)
                if (er.execPrice != null && er.execQuantity != null && er.execQuantity != 0.0) {
                    val trade = Trade(er.execId, er.orderId, er.execPrice, er.execQuantity)
                    logger.info("{}", trade)
                    trades[er.execId] = trade
                }

                when (data) {
                    is Message -> {
                        Session.sendToTarget(data)
                    }
                }
            }
            is OrderCancelRejectMessage -> {
                val (exchange, cr, data) = message
                val orderId = cr.orderId
                val order = orders.getOrElse(orderId, {Order(orderId, exchange, "", 0, pq0, Exec(),
                    arrayOf(), null, null)})

                logger.info("{}", order)
                broadcast("order", order)

                when (data) {
                    is Message -> {
                        Session.sendToTarget(data)
                    }
                }
            }

            is OrderCancelReplaceRejectMessage -> {
                val (exchange, cr, data) = message
                val orderId = cr.orderId
                val order = orders.getOrElse(orderId, {Order(orderId, exchange, "", 0, pq0, Exec(),
                    arrayOf(), null, null)})

                logger.info("{}", order)
                broadcast("order", order)

                when (data) {
                    is Message -> {
                        Session.sendToTarget(data)
                    }
                }
            }

            is NewOrderMessage -> {
                logger.info(message.toString())
                when (message.extraParams) {
                    is Message -> {
                        Session.sendToTarget(message.extraParams)
                    }
                }
            }
            is CancelOrderMessage -> {
                logger.info(message.toString())
                when (message.extraParams) {
                    is Message -> {
                        Session.sendToTarget(message.extraParams)
                    }
                }
            }
            is ReplaceOrderMessage -> {
                logger.info(message.toString())
                when (message.extraParams) {
                    is Message -> {
                        Session.sendToTarget(message.extraParams)
                    }
                }
            }
        }
    }
}

fun setup() {
    val settingsStream = ClassLoader::class.java.getResourceAsStream("/settings.txt")
    val settings = SessionSettings(settingsStream)
    val storeFactory = FileStoreFactory(settings)
    val logFactory = FileLogFactory(settings)
    val messageFactory = DefaultMessageFactory()
    val router = FixRouter(com.cashalgo.fix.routerConfig)

    val acceptor = SocketAcceptor(router, storeFactory, settings, logFactory, messageFactory)
    acceptor.start()

    val connector = SocketInitiator(router, storeFactory, settings, logFactory, messageFactory)
    connector.start()
}

fun main(args: Array<String>) {
    setup()
}

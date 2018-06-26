package com.cashalgo.fix

import com.cashalgo.fix.message.*
import quickfix.*
import quickfix.field.*
import java.time.ZoneOffset
import kotlinx.coroutines.experimental.runBlocking
import org.redisson.client.codec.JsonJacksonMapCodec
import quickfix.Message
import quickfix.fix44.*
import quickfix.fix44.ExecutionReport
import quickfix.fix44.OrderCancelReject

class FixRouter(private val config: RouterConfig): Application {
    private val idGen = IdGen()
    private val idMap = BiMap<String, String>()
    private val senderInfo = redis.getMap<String, SenderInfo>("order_senders", JsonJacksonMapCodec(String::class.java, SenderInfo::class.java))

    init {
        val x = senderInfo.map { e ->
            val senderInfo = e.value
            val k = "${senderInfo.senderCompID}|${senderInfo.clOrdId}"
            val v = e.key
            Pair(k, v)
        }.asIterable()
        idMap.restore(x)
    }

    private suspend fun handleClientMessage(message: Message) {
        val header = message.header
        val msgType = header.getField(MsgType()).value
        when (msgType) {
            MsgType.ORDER_SINGLE, MsgType.ORDER_CANCEL_REQUEST, MsgType.ORDER_CANCEL_REPLACE_REQUEST -> {}
            else -> throw RouteException("Unsupported message type")
        }

        val senderCompID = header.getField(SenderCompID()).value
        val destination = message.getString(50000)
        val strategy = message.getString(50001)
        val team = message.getString(50002)

        val route = config.routes[destination] ?: throw RouteException("Unknown destination")
        val targetCompId = route.compID

        // Replace clOrdId with own id to avoid collisions
        val clOrdId = message.getField(ClOrdID()).value
        val buysideId = "$senderCompID|$clOrdId"
        var sellSideId = idMap.get(buysideId) ?: idGen.nextId()
        idMap.put(buysideId, sellSideId)

        senderInfo[sellSideId] = SenderInfo(clOrdId, senderCompID, strategy, team)
        message.setField(ClOrdID(sellSideId))

        if (message.isSetField(OrigClOrdID.FIELD)) {
            val origClOrdId = message.getField(OrigClOrdID()).value
            val buysideOrigId = idMap.get("$senderCompID|$origClOrdId") ?: throw RouteException("Cancelling an unknown order")
            message.setField(OrigClOrdID(buysideOrigId))
        }

        message.removeField(50000)
        message.removeField(50001)
        message.removeField(50002)

        header.setField(SenderCompID("FIXROUTER"))
        header.setField(TargetCompID(targetCompId))

        when (message) {
            is NewOrderSingle -> {
                val symbol = message.get(SecurityID()).value
                val price = message.get(Price()).value
                val quantity = message.get(OrderQty()).value
                val m = NewOrderMessage(destination, clOrdId, symbol, price, quantity, message, null)
                oms.send(m)
            }
            is OrderCancelRequest -> {
                val orderId = message.get(OrderID()).value
                val correlId = message.get(OrigClOrdID()).value
                val m = CancelOrderMessage(orderId, correlId, message, null)
                oms.send(m)
            }
            is OrderCancelReplaceRequest -> {
                val orderId = message.get(OrderID()).value
                val correlId = message.get(OrigClOrdID()).value
                val price = message.get(Price()).value
                val quantity = message.get(OrderQty()).value
                val m = ReplaceOrderMessage(orderId, correlId, price, quantity, message, null)
                oms.send(m)
            }
        }
    }

    private suspend fun handleExchangeMessage(message: Message) {
        val exOrdId = message.getField(ClOrdID()).value
        val clOrdId = idMap.getByV(exOrdId) ?: throw RuntimeException("Unknown client order id $exOrdId")
        val senderInfo = senderInfo[exOrdId]!!
        val exchange = message.getField(SecurityExchange()).value
        message.setField(ClOrdID(clOrdId.split("|")[1]))

        if (message.isSetField(OrigClOrdID.FIELD)) {
            val origExOrdID = message.getField(OrigClOrdID()).value
            val origClOrdId = idMap.getByV(origExOrdID) ?: throw RuntimeException("Unknown orig client order id $exOrdId")
            message.setField(OrigClOrdID(origClOrdId.split("|")[1]))
        }

        message.header.setField(SenderCompID("FIXROUTER"))
        message.header.setField(TargetCompID(senderInfo.senderCompID))

        when (message) {
            is ExecutionReport -> {
                val execId = message[ExecID()].value
                val orderId = message[OrderID()].value
                val symbol = message[SecurityID()].value
                val price = message[Price()].value
                val quantity = message[OrderQty()].value
                val execPrice = message[LastPx()].value
                val execQuantity = message[LastQty()].value
                val cumExecPrice = message[AvgPx()].value
                val cumExecQuantity = message[CumQty()].value
                val status = message[OrdStatus()].value.toString()
                val timestamp = message[TransactTime()].value.atZone(ZoneOffset.systemDefault()).toEpochSecond()*1000
                val active = (status!="2"&&status!="4"&&status!="8"&&status!="C")
                val er = com.cashalgo.fix.message.ExecutionReport(execId, orderId, symbol, price, quantity, execQuantity, execPrice, cumExecQuantity, cumExecPrice, status, timestamp, active)
                val erm = ExecutionReportMessage(exchange, er, message)
                oms.send(erm)
            }
            is OrderCancelReject -> {
                val orderId = message[OrderID()].value
                val clOrdId = message.getField(ClOrdID()).value
                val origClOrdId = message.getField(OrigClOrdID()).value
                val status = message[OrdStatus()].value.toString()
                val cxlRejResponseTo = message[CxlRejResponseTo()].value.toString()
                val er = com.cashalgo.fix.message.OrderCancelReject(orderId, clOrdId, origClOrdId, status, cxlRejResponseTo)
                val erm = OrderCancelRejectMessage(exchange, er, message)
                oms.send(erm)
            }
        }
    }

    override fun onLogon(sessionId: SessionID?) {
    }

    override fun onLogout(sessionId: SessionID?) {
    }

    override fun onCreate(sessionId: SessionID?) {
    }

    override fun toAdmin(message: Message?, sessionId: SessionID?) {
        logger.info("Admin OUT {}", message)
    }

    override fun toApp(message: Message?, sessionId: SessionID?) {
        logger.info("OUT {}", message)
    }

    override fun fromAdmin(message: Message?, sessionId: SessionID?) {
        logger.info("Admin IN {}", message)
    }

    override fun fromApp(message: Message?, sessionId: SessionID?) {
        logger.info("IN {}", message)
        if (message == null) return

        try {
            val header = message.header
            val senderCompID = header.getField(SenderCompID()).value

            logger.info("CompID --> {}", senderCompID)

            runBlocking {
                when (senderCompID) {
                    in config.buys -> {
                        logger.info("Buy side")
                        handleClientMessage(message)
                    }

                    in config.sells -> {
                        logger.info("Sell side")
                        handleExchangeMessage(message)
                    }
                }
                logger.info("Message handled")
            }
        }
        catch (e: Exception) {
            logger.error("{}", e)
        }
    }
}

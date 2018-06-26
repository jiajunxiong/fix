package com.cashalgo.fix

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

data class Route(
    val name: String,
    val compID: String)

data class RouterConfig(
    val buys: Set<String>,
    val sells: Set<String>,
    val routes: Map<String, Route>)

data class SenderInfo(
    val clOrdId: String,
    val senderCompID: String,
    val strategy: String,
    val team: String) {
    @Suppress("unused")
    constructor(): this("", "", "", "")
}
class RouteException(message: String) : Exception(message)

val config = ConfigFactory.parseResources("config.hocon")!!

fun parseConfig(config: Config): RouterConfig {
    val routes = config.getObject("routes")
    val r = routes.mapValues { Route(it.key, it.value.unwrapped() as String) }
    return RouterConfig(config.getStringList("buys").toSet(), config.getStringList("sells").toSet(), r)
}

val routerConfig =  com.cashalgo.fix.parseConfig(config.getConfig("fixRouter"))
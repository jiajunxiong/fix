package com.cashalgo.fix

data class Trade(
    val id: String,
    val orderId: String,
    val price: Double,
    val quantity: Double)
{
    @Suppress("unused")
    constructor(): this("", "", 0.0, 0.0)
}
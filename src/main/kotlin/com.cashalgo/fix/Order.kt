package com.cashalgo.fix

data class Order(
    val id: String,
    val exch: String,
    val symbol: String,
    var version: Int,
    var pq: PQ,
    var exec: Exec,
    var prevVersions: Array<PQ>,
    var pendingCancel: String?,
    var pendingAmend: String?
) {
    @Suppress("unused")
    constructor(): this("", "", "", 0, pq0, Exec(),
        arrayOf(), null, null)
}
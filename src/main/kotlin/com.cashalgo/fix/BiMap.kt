package com.cashalgo.fix

class BiMap<K, V> {
    val k2v = HashMap<K, V>()
    val v2k = HashMap<V, K>()

    fun put(k: K, v: V) {
        k2v.put(k, v)
        v2k.put(v, k)
    }

    fun get(k: K) = k2v[k]
    fun getByV(v: V) = v2k[v]

    fun restore(kvs: Iterable<Pair<K, V>>) {
        for (kv in kvs) {
            put(kv.first, kv.second)
        }
    }
}

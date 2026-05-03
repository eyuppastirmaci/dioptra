package io.github.eyuppastirmaci.dioptra.presentation.console

import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot

class ConsoleDashboardPrinter {

    fun print(snapshot: RedisDashboardSnapshot) {
        println()
        println("Dioptra Redis Dashboard")
        println("-----------------------")
        println("Status: ${snapshot.status}")
        println("Redis version: ${snapshot.redisVersion}")
        println("Used memory: ${snapshot.usedMemoryHuman}")
        println("Connected clients: ${snapshot.connectedClients}")
        println("Total commands processed: ${snapshot.totalCommandsProcessed}")
        println("Keyspace hits: ${snapshot.keyspaceHits}")
        println("Keyspace misses: ${snapshot.keyspaceMisses}")
        println("Total keys: ${snapshot.totalKeys}")
        println()
    }
}
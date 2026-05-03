package io.github.eyuppastirmaci.dioptra.presentation.console

import io.github.eyuppastirmaci.dioptra.domain.dashboard.RedisDashboardSnapshot

class ConsoleDashboardPrinter {

    fun print(snapshot: RedisDashboardSnapshot) {
        println()
        println("Dioptra Redis Dashboard")
        println("-----------------------")
        println("Status: ${snapshot.status}")
        println("Active profile: ${snapshot.activeConnectionName}")
        println("Selected database: ${snapshot.selectedDatabase}")
        println("Server version: ${snapshot.redisVersion}")
        println("Uptime: ${snapshot.uptime}")
        println("Used memory: ${snapshot.usedMemoryHuman}")
        println("Memory fragmentation: ${snapshot.memoryFragmentationHint}")
        println("Connected clients: ${snapshot.connectedClients}")
        println("Client load: ${snapshot.connectedClientsWarning}")
        println("Blocked clients: ${snapshot.blockedClients}")
        println("Ops/sec: ${snapshot.instantaneousOpsPerSecond}")
        println("Total commands processed: ${snapshot.totalCommandsProcessed}")
        println("Keyspace hits: ${snapshot.keyspaceHits}")
        println("Keyspace misses: ${snapshot.keyspaceMisses}")
        println("Total keys: ${snapshot.totalKeys}")
        println("Maxmemory policy: ${snapshot.maxmemoryPolicy}")
        println("Evicted keys: ${snapshot.evictedKeys}")
        println()
    }
}

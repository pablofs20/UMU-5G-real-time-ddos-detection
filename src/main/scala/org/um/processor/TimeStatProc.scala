package org.um.processor

import java.util

import scala.collection.mutable
import java.util._


object TimeStatProc{
    private val CLEAR_TINE : Long = 6 * 1000
}

class TimeStatProc {

    // Map including number of new connections to a service (port number) (80 or 443) (last five seconds)
    //                                                  <SrcIp, <DstPort, count >
    private val newConnectionsService = new util.HashMap[String, util.HashMap[Int,Double]]()

    // Map including number of total connections to a service (port number) (80 or 443
    //                                                     <SrcIp, <DstPort, count >
    private val totalConnectionsService = new util.HashMap[String, util.HashMap[Int,Double]]()

    private val timer = new Timer()
    timer.schedule(new ClearTask(), 0, TimeStatProc.CLEAR_TINE)

    private def logNewConnectionsService(srcIp: String, dstPort: Int): Unit = {
        var mapSrcIp = newConnectionsService.get(srcIp)
        if (mapSrcIp == null) {
            println("ACHO1")
            mapSrcIp = new util.HashMap[Int, Double]()
            newConnectionsService.put(srcIp, mapSrcIp)
        }

        var count = mapSrcIp.getOrDefault(dstPort, -1)
        if (count == -1){
            println("ACHO2")
            mapSrcIp.put(dstPort, 0.0)
        }

        count = mapSrcIp.get(dstPort)
        mapSrcIp.put(dstPort, count + 1)
    }

    private def logTotalConnectionsService(srcIp: String, dstPort: Int): Unit = {
        var mapSrcIp = totalConnectionsService.get(srcIp)
        if (mapSrcIp == null) {
            mapSrcIp = new util.HashMap[Int, Double]()
            totalConnectionsService.put(srcIp, mapSrcIp)
        }

        var count = mapSrcIp.getOrDefault(dstPort, -1)
        if (count == -1) mapSrcIp.put(dstPort, 0.0)

        count = mapSrcIp.get(dstPort)
        mapSrcIp.put(dstPort, count + 1)
    }

    private def logOutNewConnectionsService(srcIp: String, dstPort: Int): Unit = {
        val mapSrcIp = newConnectionsService.get(srcIp)
        if (mapSrcIp == null) return

        val count = mapSrcIp.getOrDefault(dstPort, -1)
        if (count == -1) return

        mapSrcIp.put(dstPort, count - 1)
    }

    private def logOutTotalConnectionsService(srcIp: String, dstPort: Int): Unit = {
        val mapSrcIp = totalConnectionsService.get(srcIp)
        if (mapSrcIp == null) return

        val count = mapSrcIp.getOrDefault(dstPort,-1)
        if (count == -1) return

        mapSrcIp.put(dstPort, count - 1)
    }


    // Return total number of new conections and total new connections to dstPorst  (for srcIp)
    private def getNewConnectionServiceRate(srcIp: String, dstPort: Integer): util.List[Double] = {
        val mapSrcIp = newConnectionsService.get(srcIp)
        if (mapSrcIp == null) return null

        val rtn = new util.ArrayList[Double](2)

        // Total Number of connections from host
        val connections = mapSrcIp.values.stream.mapToDouble(e=> e).sum
        if (connections == 0) return null

        rtn.add(connections)

        // Total number of connections using dstPort
        val selfCountPort = mapSrcIp.getOrDefault(dstPort, -1)
        if (selfCountPort == -1 || (selfCountPort == 0)) rtn.add(0.0)
        else rtn.add(selfCountPort)

        rtn
    }

    // Return total number of connections and total connections to dstPorst  (for srcIp)
    private def getTotalConnectionServiceRate(srcIp: String, dstPort: Integer): util.List[Double] = {
        val mapSrcIp = totalConnectionsService.get(srcIp)
        if (mapSrcIp == null) return null

        val rtn = new util.ArrayList[Double](2)

        // Total Number of connections from host
        val connections = mapSrcIp.values.stream.mapToDouble(e=> e).sum
        if (connections == 0) return null

        rtn.add(connections)

        // Total number of connections using dstPort
        val selfCountPort = mapSrcIp.getOrDefault(dstPort,-1)
        if (selfCountPort == -1 || (selfCountPort == 0)) rtn.add(0.0)
        else rtn.add(selfCountPort)

        rtn
    }

    def log(srcIp: String, dstPort: Integer): Unit = synchronized{
        logNewConnectionsService(srcIp, dstPort)
        logTotalConnectionsService(srcIp, dstPort)
    }

    def logOut(srcIp: String, dstPort: Integer): Unit = synchronized{
        // useless if clear time is 5 sec
        //logOutNewConnectionsService(srcIp,dstPort);
        logOutTotalConnectionsService(srcIp, dstPort)
    }

    private def clearNewConnections(): Unit = synchronized{
        newConnectionsService.clear()
    }

    def getStats(srcIp: String, dstPort: Integer): util.List[Double] = {
        val stats = new util.LinkedList[Double]()
        // Time related features
        val newFeature = getNewConnectionServiceRate(srcIp, dstPort)
        if (newFeature == null) {
            stats.add(0.0)
            stats.add(0.0)
            stats.add(0.0)
            stats.add(0.0)
        }
        else {
            stats.addAll(newFeature)
            val percentage = newFeature.get(1) / newFeature.get(0)
            stats.add(percentage)
            stats.add(1 - percentage)
        }
        val totalFeature = getTotalConnectionServiceRate(srcIp, dstPort)
        if (totalFeature == null) {
            stats.add(0.0)
            stats.add(0.0)
            stats.add(0.0)
            stats.add(0.0)
        }
        else {
            stats.addAll(totalFeature)
            val percentage = totalFeature.get(1) / totalFeature.get(0)
            stats.add(percentage)
            stats.add(1 - percentage)
        }

        stats
    }



    class ClearTask extends TimerTask{
        override def run(): Unit = {
            clearNewConnections()
        }
    }

}

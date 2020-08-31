package org.um.processor

import java.util

import scala.collection.JavaConverters._
import com.typesafe.scalalogging.Logger
import org.um.utils.JsonMonitorizationParser

object Flow{
    /**
     * Internal enum to determine the state of the flow.
     */
    object FlowState extends Enumeration {
        val NEW, OPEN, FIN, TWO_FIN, FINACK, CLOSED = Value
    }

}

class Flow(flowID: FlowID, json : JsonMonitorizationParser) {

    private val logger = Logger(getClass)


    // State of the flow
    private var state: Flow.FlowState.Value = Flow.FlowState.NEW

    /**
     * Representation of a network flow ID.
     * It contains an ID object (used to easily identify the flow)
     */
    private val id: FlowID = flowID

    // Ips and Ports of up/down links
    private var upIP: String = _
    private var downIP: String = _
    private var upPort: String = _
    private var downPort: String = _

    // Start packet and last analyzed packet
    private var startTstamp: Long = _
    private var lastTstamp: Long = _

    // Packets count (0: Up, 1: Down)
    private val pkgCount = new Array[Double](2)
    // Bytes count (0: Up, 1: Down)
    private val bytesCount = new Array[Double](2)

    // Packets statistics (0: Up, 1: Down)
    private val pkgAvgSize = new Array[Double](2)
    private val pkgMaxSize = new Array[Double](2)
    private val pkgMinSize = new Array[Double](2)

    // TCP Window statistic (0: Up, 1: Down)
    private val windowAvg = new Array[Double](2)
    private val windowMax = new Array[Double](2)
    private val windowMin = new Array[Double](2)

    // TTL statistics (0: Up, 1: Down
    private val ttlAvg = new Array[Double](2)
    private val ttlMax = new Array[Double](2)
    private val ttlMin = new Array[Double](2)

    // TCP flags statistic (0: Up, 1: Down)
    private val fin = new Array[Double](2)
    private val syn = new Array[Double](2)
    private val rst = new Array[Double](2)
    private val psh = new Array[Double](2)
    private val ack = new Array[Double](2)
    private val urg = new Array[Double](2)

    // SSL Packets Count
    private val sslPkgCount = new Array[Double](2)

    // SSL Content Type (0: Up, 1: Down)
    private val chgCipher = new Array[Double](2)
    private val alert = new Array[Double](2)
    private val handshake = new Array[Double](2)
    private val appData = new Array[Double](2)
    private val heartbeat = new Array[Double](2)

    // Time and global features (Relative)
    private var sameConnectionsRelative: Double = 0
    private var diffConnectionsRelative: Double = 0
    private var percentageSameConnectionsRelative: Double = 0
    private var percentageDiffConnectionsRelative: Double = 0

    // Time and global features (Total)
    private var sameConnectionsTotal: Double = 0
    private var diffConnectionsTotal: Double = 0
    private var percentageSameConnectionsTotal: Double = 0
    private var percentageDiffConnectionsTotal: Double = 0

    // Indicates who started TCP Termination.
    var terminationSense = 0


    Array(0, 1).toStream.foreach(i => {
        pkgCount(i) = 0
        bytesCount(i) = 0
        pkgAvgSize(i) = 0
        pkgMaxSize(i) = 0
        pkgMinSize(i) = Double.MaxValue
        windowAvg(i) = 0
        windowMax(i) = 0
        windowMin(i) = Double.MaxValue
        ttlAvg(i) = 0
        ttlMax(i) = 0
        ttlMin(i) = Double.MaxValue
        sslPkgCount(i) = 0
        fin(i) = 0
        syn(i) = 0
        psh(i) = 0
        ack(i) = 0
        urg(i) = 0
        chgCipher(i) = 0
        alert(i) = 0
        handshake(i) = 0
        appData(i) = 0
        heartbeat(i) = 0
    })

    def logPkg(id: FlowID, pkgInfo: Array[String]): Boolean = {
        if (id != this.id) {
            logger.error("Trying to log a pkg that do not belong to this flow. Actual id:" + this.id
                + ", pkg id: " + id)
        }

        // The flow is closed, a package cannot be logged here.
        if (state == Flow.FlowState.CLOSED) return false

        // Get the timestamp
        // Get rid of the "." and parse the stamp in microsec.
        var tstampStr = pkgInfo(3).replace(".", "")
        var tstamp = tstampStr.toLong


        // Log up/down ips and ports if New flow and tstamp
        if (state == Flow.FlowState.NEW) {
            upIP = pkgInfo(6)
            downIP = pkgInfo(7)
            upPort = pkgInfo(8)
            downPort = pkgInfo(9)
            this.startTstamp = tstamp
            this.lastTstamp = tstamp
            state = Flow.FlowState.OPEN
        }
        // Sanitize tstamp. Make sure it has the same amount of digits
        else
            {
                val lengthInit = this.startTstamp.toString.length
                val lengthNow = tstampStr.length

                if (lengthNow != lengthInit){
                    while (lengthNow < lengthInit){
                        tstampStr += '0'
                    }
                    while (lengthNow > lengthInit){
                        tstampStr = tstampStr.substring(0, tstampStr.length - 1)
                    }
                    tstamp = tstampStr.toLong
                }
            }

        // This function uses all the information of the logged packet.
        // So a displacement is required to access the statistics fields
        val displacement = 10
        val size = pkgInfo(displacement).toDouble
        val tcpwin = pkgInfo(displacement + 1).toDouble
        val contType = pkgInfo(displacement + 2).toDouble
        val ttl = pkgInfo(displacement + 3).toDouble
        val fin = pkgInfo(displacement + 4).toDouble
        val syn = pkgInfo(displacement + 5).toDouble
        val rst = pkgInfo(displacement + 6).toDouble
        val psh = pkgInfo(displacement + 7).toDouble
        val ack = pkgInfo(displacement + 8).toDouble
        val urg = pkgInfo(displacement + 9).toDouble

        // Determine if the packet is uplink (0) or downlink (1)
        // NOTE: This checking assumes that the first packet seen is the "uplink"
        val pkgSrcIP = pkgInfo(6)
        val pkgDstIP = pkgInfo(7)
        val pkgSrcPort = pkgInfo(8)
        val pkgDstPort = pkgInfo(9)
        val sense = if (pkgSrcIP.equals( upIP) && pkgSrcPort.equals(upPort)) 0 else 1
        /*logger.info("RESULTADO : UpIP = " + upIP + " UpPort = " + upPort +
             " DownIP = " + downIP + " DownPort = " + downPort +
            " pkgSrcIP = " + pkgSrcIP + " pkgSrcPort = " + pkgSrcPort +
            " pkgDstIP = " + pkgDstIP + " pkgDstPort = " + pkgDstPort +
        " SEnse" + sense)*/

        if (!( (pkgSrcIP == upIP && pkgDstIP == downIP && pkgSrcPort == upPort && pkgDstPort == downPort) ||
            (pkgSrcIP == downIP && pkgDstIP == upIP && pkgSrcPort == downPort && pkgDstPort == upPort)))
            logger.error("ERROR GRAVISIMO")


        // Update the corresponding statistics// Update the corresponding statistics

        // Timestamp
        // maybe an early packet arrived now
        if (tstamp < this.startTstamp) this.startTstamp = tstamp
        else this.lastTstamp = tstamp


        // Packet counting
        this.pkgCount(sense) += 1
        // Bytes counting
        this.bytesCount(sense) += size
        // Packets statistics
        this.pkgAvgSize(sense) += (size - this.pkgAvgSize(sense)) / this.pkgCount(sense)
        if (this.pkgMaxSize(sense) < size) this.pkgMaxSize(sense) = size
        if (this.pkgMinSize(sense) > size) this.pkgMinSize(sense) = size
        // TCP Window statistics
        this.windowAvg(sense) += (tcpwin - this.windowAvg(sense)) / this.pkgCount(sense)
        if (this.windowMax(sense) < tcpwin) this.windowMax(sense) = tcpwin
        if (this.windowMin(sense) > tcpwin) this.windowMin(sense) = tcpwin
        // TTL
        this.ttlAvg(sense) += (ttl - this.ttlAvg(sense)) / this.pkgCount(sense)
        if (this.ttlMax(sense) < ttl) this.ttlMax(sense) = ttl
        if (this.ttlMin(sense) > ttl) this.ttlMin(sense) = ttl
        // TCP flags statistics
        this.fin(sense) += fin
        this.syn(sense) += syn
        this.rst(sense) += rst
        this.psh(sense) += psh
        this.ack(sense) += ack
        this.urg(sense) += urg
        // Number of SSL packets
        this.sslPkgCount(sense) += 1
        // Type of SSL content
        contType match {
            case 20 => this.chgCipher(sense) += 1
            case 21 => this.alert(sense) += 1
            case 22 => this.handshake(sense) += 1
            case 23 => this.appData(sense) += 1
            case 24 => this.heartbeat(sense) += 1
            case _ =>
        }


        // Update the state of the flow depending on the FIN flag
        // WARNING: This behavior assumes the FIN, FIN-ACK, ACK packets arrive IN ORDER!!!!
        // At this point, the state of the connection is, at least, open
        // so if I see a FIN packet, I need to update the state.
        // First Host wants to terminate connection
        if ((this.state == Flow.FlowState.OPEN) && (fin == 1)) {
            terminationSense = sense
            this.state = Flow.FlowState.FIN
        }
        // Second host replies back
        else if (this.state == Flow.FlowState.FIN && terminationSense != sense) {
            if (rst == 1) {
                this.state = Flow.FlowState.CLOSED
            }
            // Four-way handshake
            else if ((fin == 1) && (ack == 0)) {
                this.state = Flow.FlowState.TWO_FIN
                logger.info("TWOFIN")
            }
            // Three way handshake
            else if ((fin == 1) && (ack == 1)) {
                this.state = Flow.FlowState.FINACK
            }
        }

        // Four-way handshake
        else if (this.state == Flow.FlowState.TWO_FIN && terminationSense != sense) {
            if (rst == 1) {
                this.state = Flow.FlowState.CLOSED
            }
            else if (ack == 1) {
                this.state = Flow.FlowState.FINACK
            }
        }

        // Last Ack
        else if (this.state == Flow.FlowState.FINACK && terminationSense == sense) {
            if ((ack == 1) || (rst == 1)) {
                this.state = Flow.FlowState.CLOSED
                logger.info("Closing Natural")
            }
        }

        // If state is OPEN and receive RST, CLOSE Flow
        else if (this.state == Flow.FlowState.OPEN && rst == 1)
            this.state = Flow.FlowState.CLOSED

        true
    }

    /**
     * Gather the features corresponding to this flow without socket information.
     *
     * @return list of features
     */
    private def getFlowStatistics: util.List[String] = {
        // Create the list of results to return
        val results = new util.LinkedList[String]()
        // Duration
        var duration = this.lastTstamp - this.startTstamp

        /*logger.info("Duratione +" + duration)
        logger.info("lastts +" + lastTstamp)
        logger.info("startts+" + startTstamp)
        logger.info("pkcount " + pkgCount(0) + " " + pkgCount(1))*/

        // if tiny conversation do not send (useless)
       /* if (duration < 1000 || this.pkgCount(0) < 5 || this.pkgCount(1) < 5) {
            return null
        }*/
        /*
        if (this.pkgCount(0) < 1 || this.pkgCount(1) < 1) {
            return null
        }
        */

        results.add(duration.toString)

        duration = duration/1000000 // microsec to sec

        if (duration > 1000000)
            logger.info("aquipasa " + duration +" "+ startTstamp +" "+ lastTstamp)

        logger.info(upPort + " pkcount " + pkgCount(0) + " " + pkgCount(1) +  " " + duration)

        // Packets and bytes in 1 second (up and down)
        if (duration > 0) {
            results.add((this.pkgCount(0) / duration).toString)
            results.add((this.pkgCount(1) / duration).toString)
            results.add((this.bytesCount(0) / duration).toString)
            results.add((this.bytesCount(1) / duration).toString)
        } else {
            results.add(this.pkgCount(0).toString)
            results.add(this.pkgCount(1).toString)
            results.add(this.bytesCount(0).toString)
            results.add(this.bytesCount(1).toString)
        }

        // Max, Min and Avg pkg size in up and downlink
        results.add(this.pkgMaxSize(0).toString)
        results.add(this.pkgMinSize(0).toString)
        results.add(this.pkgAvgSize(0).toString)
        results.add(this.pkgMaxSize(1).toString)
        results.add(this.pkgMinSize(1).toString)
        results.add(this.pkgAvgSize(1).toString)
        // Max, Min and Avg TCP window size in up and downlink
        results.add(this.windowMax(0).toString)
        results.add(this.windowMin(0).toString)
        results.add(this.windowAvg(0).toString)
        results.add(this.windowMax(1).toString)
        results.add(this.windowMin(1).toString)
        results.add(this.windowAvg(1).toString)
        // Max, Min and Avg TTL value in up and downlink
        results.add(this.ttlMax(0).toString)
        results.add(this.ttlMin(0).toString)
        results.add(this.ttlAvg(0).toString)
        results.add(this.ttlMax(1).toString)
        results.add(this.ttlMin(1).toString)
        results.add(this.ttlAvg(1).toString)

        // Percentage of packets for each flag in up and downlink
        if (this.pkgCount(0) > 0) {
            results.add((this.fin(0) / this.pkgCount(0)).toString)
            results.add((this.syn(0) / this.pkgCount(0)).toString)
            results.add((this.rst(0) / this.pkgCount(0)).toString)
            results.add((this.psh(0) / this.pkgCount(0)).toString)
            results.add((this.ack(0) / this.pkgCount(0)).toString)
            results.add((this.urg(0) / this.pkgCount(0)).toString)
        } else {
            results.add(this.fin(0).toString)
            results.add(this.syn(0).toString)
            results.add(this.rst(0).toString)
            results.add(this.psh(0).toString)
            results.add(this.ack(0).toString)
            results.add(this.urg(0).toString)
        }

        if (this.pkgCount(1) > 0) {
            results.add((this.fin(1) / this.pkgCount(1)).toString)
            results.add((this.syn(1) / this.pkgCount(1)).toString)
            results.add((this.rst(1) / this.pkgCount(1)).toString)
            results.add((this.psh(1) / this.pkgCount(1)).toString)
            results.add((this.ack(1) / this.pkgCount(1)).toString)
            results.add((this.urg(1) / this.pkgCount(1)).toString)
        } else {
            results.add(this.fin(1).toString)
            results.add(this.syn(1).toString)
            results.add(this.rst(1).toString)
            results.add(this.psh(1).toString)
            results.add(this.ack(1).toString)
            results.add(this.urg(1).toString)
        }

        // Percentage of packets with different properties
        if (this.sslPkgCount(0) > 0) {
            results.add((this.chgCipher(0) / this.sslPkgCount(0)).toString)
            results.add((this.alert(0) / this.sslPkgCount(0)).toString)
            results.add((this.handshake(0) / this.sslPkgCount(0)).toString)
            results.add((this.appData(0) / this.sslPkgCount(0)).toString)
            results.add((this.heartbeat(0) / this.sslPkgCount(0)).toString)
        } else {
            results.add(this.chgCipher(0).toString)
            results.add(this.alert(0).toString)
            results.add(this.handshake(0).toString)
            results.add(this.appData(0).toString)
            results.add(this.heartbeat(0).toString)
        }

        if (this.sslPkgCount(1) > 0) {
            results.add((this.chgCipher(1) / this.sslPkgCount(1)).toString)
            results.add((this.alert(1) / this.sslPkgCount(1)).toString)
            results.add((this.handshake(1) / this.sslPkgCount(1)).toString)
            results.add((this.appData(1) / this.sslPkgCount(1)).toString)
            results.add((this.heartbeat(1) / this.sslPkgCount(1)).toString)
        } else {
            results.add(this.chgCipher(1).toString)
            results.add(this.alert(1).toString)
            results.add(this.handshake(1).toString)
            results.add(this.appData(1).toString)
            results.add(this.heartbeat(1).toString)
        }

        //time and host based statistics
        results.add(sameConnectionsRelative.toString)
        results.add(diffConnectionsRelative.toString)
        results.add(percentageSameConnectionsRelative.toString)
        results.add(percentageDiffConnectionsRelative.toString)
        results.add(sameConnectionsTotal.toString)
        results.add(diffConnectionsTotal.toString)
        results.add(percentageSameConnectionsTotal.toString)
        results.add(percentageDiffConnectionsTotal.toString)


        results
    }

    def getConversation : List[String] = {
        val stats = getFlowStatistics
        if (stats == null) return null

        val features = new util.LinkedList[String]()

        // Socket information
        features.add(upIP)
        features.add(downIP)
        features.add(upPort)
        features.add(downPort)

        // add properties
        features.addAll(stats)

        features.asScala.toList
    }


    /**
     * Sets the time and host related features
     * @param stats list of time and host related features (of class TimeStatProc)
     */
    def setTimeAndHostrelatedStatistics(stats :util.List[Double]): Unit ={
        sameConnectionsRelative = stats.get(0)
        diffConnectionsRelative = stats.get(1)
        percentageSameConnectionsRelative = stats.get(2)
        percentageDiffConnectionsRelative = stats.get(3)
        sameConnectionsTotal = stats.get(4)
        diffConnectionsTotal = stats.get(5)
        percentageSameConnectionsTotal = stats.get(6)
        percentageDiffConnectionsTotal = stats.get(7)
    }

    def getJson: String ={
        json.addNewFeatures(getConversation.mkString(","))
    }

    /**
     * Returns the current state of the conversation
     * @return state of conversation
     */
    def getState: Flow.FlowState.Value = {
        state
    }

    def getDuration : Double = {
        lastTstamp - startTstamp
    }

    def getUpIP : String = {
        upIP
    }
    def getUpPort : String = {
        upPort
    }
    def getDownIP : String = {
        downIP
    }
    def getDownPort : String = {
        downPort
    }
    def getFlowID : FlowID = {
        flowID
    }

}

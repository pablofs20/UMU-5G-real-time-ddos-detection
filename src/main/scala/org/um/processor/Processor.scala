package org.um.processor

import java.util

import com.typesafe.scalalogging.Logger
import org.um.producer.ConversationSender
import org.um.utils.JsonMonitorizationParser

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.control.Breaks

class Processor(timeStatProc: TimeStatProc) {
    private val logger = Logger(getClass)
    private val timeOutChecker = new TimeOutChecker(this)
    private val flows = new mutable.HashMap[FlowID, Flow]()
    private val sender = new ConversationSender()

    timeOutChecker.startChecking()

    def processEntries(entries: java.lang.Iterable[String]): Unit = synchronized {
        /* val sorted = entries.asScala.toStream.sortWith((s0,s1) =>{
             val tstamp0 = JsonMonitorizationParser.getTimeStamp(s0).replace(".","").replaceAll("\"","").toLong
             val tstamp1 = JsonMonitorizationParser.getTimeStamp(s1).replace(".","").replaceAll("\"","").toLong

             tstamp0 < tstamp1
         })*/
        val sorted = entries.asScala.toList
        logger.info("hola! " + this + " " + sorted.toString())

        var error = false
        sorted.foreach(entryRaw => {
            val jsonParser = new JsonMonitorizationParser(entryRaw)
            val entry = jsonParser.parseJson().split(",")
            logger.info("que pasa" + this + entry.mkString(","))

            /*
            // Split into each feature
            val entry = entryRaw.split(",")
            */
            // Get the flowID of this unclassified network packet
            val id = new FlowID(entry(6), entry(7), entry(8), entry(9))
            // Check if this packet belongs to an already-registered flow
            if (flows.get(id).isDefined) {
                // There is a flow with this ID
                // Get the stored conversation.
                val flow = flows(id)
                flow.logPkg(id, entry)
            } else {
                val syn = entry(15).toInt
                val ack = entry(18).toInt
                if (syn == 0 || ack == 1) {
                    logger.info("breaking " + entry(6) + " " + entry(7) + " " + entry(8) + " " + entry(9))
                    if (ack == 1) logger.info("unoack " + entryRaw)
                    error = true
                } else {
                    if (entry(6).equals("172.99.0.2")) {
                        logger.info("Algo raro" + entryRaw)
                    }

                    // A new flow should be created
                    val newFlow = new Flow(id, jsonParser)
                    newFlow.logPkg(id, entry)
                    flows.put(id, newFlow)

                    // Log time and host-based statistics
                    timeStatProc.log(newFlow.getUpIP, newFlow.getDownPort.toInt)
                }

            }
            if (!error) {
                // Add flow or log to time out checker
                timeOutChecker.addFlow(id)

                // If flow is closed then send it to kafka
                if (flows(id).getState == Flow.FlowState.CLOSED) {
                    val closedFlow = flows(id)

                    // Stop checking timeout
                    timeOutChecker.removeFlow(id)

                    // Remove it from the map
                    flows.remove(id)

                    // add host and time based stats
                    closedFlow.setTimeAndHostrelatedStatistics(
                        timeStatProc.getStats(closedFlow.getUpIP, closedFlow.getDownPort.toInt))

                    //Send it
                    logger.info("Sending Closed " + closedFlow.getConversation)
                    sender.sendConversation(closedFlow)

                    //log out
                    timeStatProc.logOut(closedFlow.getUpIP, closedFlow.getDownPort.toInt)
                }
            }
            error = false
        })

    }

    def removeFlow(flowID: FlowID): Unit = synchronized {
        flows.remove(flowID)
    }

    def getFlow(flowID: FlowID): Flow = {
        flows(flowID)
    }

    def getTimeStatProc: TimeStatProc = {
        timeStatProc
    }

}

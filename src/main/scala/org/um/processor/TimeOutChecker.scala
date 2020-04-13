package org.um.processor

import java.util
import java.util.concurrent.ConcurrentHashMap
import java.util.{Timer, TimerTask}

import com.typesafe.scalalogging.Logger
import org.um.producer.ConversationSender


object TimeOutChecker {
    private val TIMEOUT_SEND: Long = 5 * 1000
    private val TIME_CHECK: Long = TIMEOUT_SEND
    private val TIMEOUT_DELETE: Long = 60 * 1000
}

class TimeOutChecker(processor: Processor) {

    private class TimeValue(time: Double) {
        val timeStart : Double = time
        var timeLastPacket : Double= time
        var newPacketReceived = true
    }


    private val logger = Logger(getClass)

    private val timer = new Timer()
    private val flowTimeMap = new ConcurrentHashMap[FlowID, TimeValue]()
    private var checking = false
    private val sender = new ConversationSender()


    def startChecking(): Unit = {
        if (checking) return

        checking = true
        timer.schedule(new TimeOutTask, 0, TimeOutChecker.TIME_CHECK)
    }

    def addFlow(flowID: FlowID): Unit = synchronized{
        val time = flowTimeMap.get(flowID)
        if (time == null){
            flowTimeMap.put(flowID, new TimeValue(System.currentTimeMillis()))
            return
        }

        time.timeLastPacket = System.currentTimeMillis()
        time.newPacketReceived = true

    }

    def removeFlow(flowID: FlowID): Unit = synchronized{
        flowTimeMap.remove(flowID)
    }

    class TimeOutTask extends TimerTask {
        private val toBeDeleted = new util.LinkedList[FlowID]()

        override def run(): Unit = {
            logger.info("Cheking for timeouts " + this)
            val timeNow = System.currentTimeMillis()
            toBeDeleted.clear()
            flowTimeMap.forEach((flowID, time) =>{
                val flow = processor.getFlow(flowID)
                // If timeout and flow duration is > 5 sec
                if (time.newPacketReceived && timeNow - time.timeStart > TimeOutChecker.TIMEOUT_SEND){
                    // send it
                    time.newPacketReceived = false
                    flow.setTimeAndHostrelatedStatistics(
                        processor.getTimeStatProc.getStats(flow.getUpIP, flow.getDownPort.toInt))
                    sender.sendConversation(flow)

                    logger.info("Sending Timeout " + this +" " + processor.getFlow(flowID).getConversation)

                }
                else if(timeNow - time.timeLastPacket > TimeOutChecker.TIMEOUT_DELETE) {
                    toBeDeleted.add(flowID)
                    processor.getTimeStatProc.logOut(flow.getUpIP, flow.getDownPort.toInt)

                }



            })

            toBeDeleted.forEach(flowID =>{
                logger.info("Deleting Timeout" + processor.getFlow(flowID).getConversation)
                flowTimeMap.remove(flowID)
                processor.removeFlow(flowID)
            })

        }
    }


}

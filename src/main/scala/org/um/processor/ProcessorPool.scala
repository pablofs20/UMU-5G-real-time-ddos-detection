package org.um.processor

import scala.collection.mutable

object ProcessorPool {
    private val processors: mutable.HashMap[Int, Processor] = new mutable.HashMap()
    private val timeStatProc = new TimeStatProc()

    def getProcessor(partitionId: Int): Processor = {
        processors.getOrElseUpdate(partitionId, new Processor(timeStatProc))
    }
}

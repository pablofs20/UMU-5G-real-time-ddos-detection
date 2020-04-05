package org.um.processor

import scala.collection.mutable

object ProcessorPool{
    private var processors: mutable.HashMap[Int,Processor] = new mutable.HashMap()

    def getProcessor(partitionId : Int): Processor ={
        processors.getOrElseUpdate(partitionId, new Processor())
    }
}

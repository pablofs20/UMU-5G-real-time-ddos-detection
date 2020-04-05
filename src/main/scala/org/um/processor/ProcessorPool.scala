package org.um.processor

import scala.collection.mutable

object ProcessorPool{
    private var processors: mutable.HashMap[Int,Processor] = new mutable.HashMap()

    def getProcessor(partitionId : Int): Processor ={
        if (processors.getOrElse(partitionId, None) == None)
            processors += (partitionId -> new Processor())

        processors.apply(partitionId)
    }
}

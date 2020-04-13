package org.um.processor

class FlowID(sourceIP: String, destIP: String, sourcePort: String, destPort: String) {
    val id: Long = setID()

    private def setID(): Long = {
        sourceIP.hashCode * destIP.hashCode * sourcePort.hashCode * destPort.hashCode
    }

    override def equals(o: Any): Boolean = o.asInstanceOf[FlowID].id == id

    override def hashCode(): Int = id.toInt
}

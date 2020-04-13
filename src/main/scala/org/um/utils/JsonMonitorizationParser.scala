package org.um.utils

import java.util

import play.api.libs.json._

object JsonMonitorizationParser extends App{
    def getTimeStamp(json : String) : String = {
        val parsed = Json.parse(json)
        val features = parsed\"data"\2

        features("timestamp").toString()
    }

    def getIPsPorts(json : String): util.List[String] ={
        val rtn = new util.LinkedList[String]()
        val parsed = Json.parse(json)
        val features = parsed\"data"\2

        rtn.add(features("outSrcIP").toString())
        rtn.add(features("outDstIP").toString())
        rtn.add(features("srcPort").toString())
        rtn.add(features("dstPort").toString())

        rtn
    }
}

class JsonMonitorizationParser(json: String){

    def parseJson() : String = {
        val rtn = new util.LinkedList[String]()
        val parsed = Json.parse(json)
        val features = parsed\"data"\2

        rtn.add("reportType")
        rtn.add("probeID")
        rtn.add("networkInterface")
        rtn.add(features("timestamp").toString())
        rtn.add("reportNumber")
        rtn.add("networkEvent")
        rtn.add(features("srcIP").toString())
        rtn.add(features("dstIP").toString())
        rtn.add(features("srcPort").toString())
        rtn.add(features("dstPort").toString())
        rtn.add(features("ipTotalLength").toString())
        rtn.add(features("tcpWindowSize").toString())
        rtn.add(features("ipTTL").toString())
        rtn.add(features("tcpFin:").toString())
        rtn.add(features("tcpSyn:").toString())
        rtn.add(features("tcpRst:").toString())
        rtn.add(features("tcpPsh:").toString())
        rtn.add(features("tcpAck:").toString())
        rtn.add(features("tcpUrg:").toString())


        String.join(",",rtn).replaceAll("\"","")
    }

    def asString(): String ={
        json
    }

    def addNewFeatures(features : String): String ={
        val parsed = Json.parse(json)
        val transformer = (__ \'key2 ).json.update(__.read[JsString].map(__ => Json.toJson("asd")))
        parsed.transform(transformer)
        val features0 = (parsed\"data"\0).as[JsObject]
        val features1 = (parsed\"data"\1).as[JsObject]
        val features2 = (parsed\"data"\2).as[JsObject]
        val obj = Json.obj(
            "data" -> Json.arr(
                features0,
                features1,
                Json.obj(
                    "encapsulationLayer" -> features2("encapsulationLayer"),
                    "encapsulationID1" -> features2("encapsulationID1"),
                    "uplinkIP" -> features2("srcIP"),
                    "downlinkIP" -> features2("dstIP"),
                    "uplinkPort" -> features2("srcPort"),
                    "downlinkPort" -> features2("dstPort"),
                    "outSrcIP" -> features2("outSrcIP"),
                    "outDstIP" -> features2("outDstIP"),
                    "l4Proto" -> features2("l4Proto"),
                    "l7Proto" -> features2("l7Proto"),
                ),
                Json.obj(
                    "features" -> features
                )
            )

        )

        obj.toString()

    }







}

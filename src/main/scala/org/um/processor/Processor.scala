package org.um.processor


class Processor {

    private val timeOutChecker = new TimeOutChecker(this)

    timeOutChecker.startChecking()

    def foo( entries : java.lang.Iterable[String]): Unit = synchronized{
        println("loko " + Thread.currentThread.getId + " " + System.currentTimeMillis)
        Thread.sleep(50000)
        println("lokoFin " + Thread.currentThread.getId + " " + System.currentTimeMillis)

    }
}

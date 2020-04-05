package org.um.processor


class Processor {

    def foo( entries : java.lang.Iterable[String]): Unit ={
        println("loko " + Thread.currentThread.getId + " " + System.currentTimeMillis)
        Thread.sleep(50000)
        println("lokoFin " + Thread.currentThread.getId + " " + System.currentTimeMillis)
    }
}

package org.um.processor

import java.util.{Timer, TimerTask}

import com.typesafe.scalalogging.Logger

object TimeOutChecker{
  val TIMEOUT_SEND : Long = 5 * 1000
  val TIME_CHECK : Long = TIMEOUT_SEND
  val TIMEOUT_DELETE : Long = 60 * 1000
}

class TimeOutChecker(processor: Processor) {
    private val logger = Logger(getClass)
    private val timer = new Timer()
    private var checking = false

    private val this.processor = processor


    def startChecking(): Unit ={
      if (checking) return

      checking = true
      timer.schedule(new TimeOutTask, 0, TimeOutChecker.TIME_CHECK)
    }

  class TimeOutTask extends TimerTask{
    override def run(): Unit = {
      logger.info("Estoy dentro de " + processor)
    }
  }
}

package app

import akka.actor.ActorSystem
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

class BetTimer(delay: Int) {

  var timCounter = 0
  var timState = 0

  var frstTimVal = delay
  var sndTimVal = delay
  var thrdTimVal = delay

  def under3(): Boolean = {
    if (timCounter < 3){
      return true
    }
    else return false
  }

  def frstTimTick(){
    frstTimVal -= 1
    //println(frstTimVal, toString())
  }

  def startFrstTim(): Unit ={
    timCounter += 1
    timState += 1
    val system = ActorSystem()
    for (x <- 1 to delay){
      system.scheduler.scheduleOnce(x*1000 milliseconds)({
        frstTimTick()
      })
    }
    system.scheduler.scheduleOnce(delay*1200 milliseconds)({
      timState -= 1
      timCounter -= 1
      frstTimVal = delay
    })
  }

  def sndTimTick(){
    sndTimVal -= 1
    //println(sndTimVal, toString())
  }

  def startSndTim(): Unit ={
    timCounter += 1
    timState += 1
    val system = ActorSystem()
    for (x <- 1 to delay){
      system.scheduler.scheduleOnce(x*1000 milliseconds)({
        sndTimTick()
      })
    }
    system.scheduler.scheduleOnce(delay*1000 milliseconds)({
      timState -= 1
      timCounter -= 1
      sndTimVal = delay
    })
  }

  def thrdTimTick(){
    thrdTimVal -= 1
    //println(thrdTimVal, toString())
  }

  def startThrdTim(): Unit ={
    timCounter += 1
    timState += 1
    val system = ActorSystem()
    for (x <- 1 to delay){
      system.scheduler.scheduleOnce(x*1000 milliseconds)({
        thrdTimTick()
      })
    }
    system.scheduler.scheduleOnce(delay*1000 milliseconds)({
      timState -= 1
      timCounter -= 1
      thrdTimVal = delay
    })
  }


  def resetTim(): Unit ={
    var timState = 0
    var frstTimVal = delay
    var sndTimVal = delay
    var thrdTimVal = delay
  }

  def reprTimer(): String = {
    if (timState == 0) return ""
    else if (timState == 1) return frstTimVal.toString
    else if (timState == 2) return frstTimVal + " - " + sndTimVal
    else if (timState == 3) return frstTimVal + " - " + sndTimVal + " - " + thrdTimVal
    else return ""
  }

  override def toString(): String = {
    reprTimer()
  }
}
package app

import java.net.URL
import javafx.application.Platform
import javafx.scene.media.{Media, MediaPlayer}

import app.controllers.service
import com.betfair.domain.{PriceData, PriceProjection}

import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * Created by backrus on 29/11/16.
  */
class AlarmMarket(mName: String, mfName: String, mId: String, rId: Long, aOdds: Double, auo: String) {
  val marketName: String = mName
  val marketFullName: String = mfName
  val marketId: String = mId
  val runnerId: Long = rId
  var alarmOdds: Double = aOdds
  val uo: String = auo

  var alarmDisabled: Boolean = false

  def changeAlarmOdds(newAlarmOdds: Double): Unit ={
    alarmOdds = newAlarmOdds
  }

  def playAlarm(): Unit ={
    val file: URL = this.getClass().getClassLoader().getResource("alarm.mp3")
    val media: Media = new Media(file.toString())
    val mediaPlayer: MediaPlayer = new MediaPlayer(media)
    mediaPlayer.play()
    //mediaPlayer.stop()
  }

  def checkAlarmMarket(): Unit ={
    val priceProjection = PriceProjection(priceData = Set(PriceData.EX_BEST_OFFERS))
    service.betfairServiceNG.listMarketBook(service.sessionToken, marketIds = Set(marketId),
      priceProjection = Some(("priceProjection", priceProjection))) onComplete {
      case Success(Some(listMarketBookContainer)) =>
        for (marketBook <- listMarketBookContainer.result) {
          for (runner <- marketBook.runners) {
            if (runner.selectionId.equals(runnerId)) {
              if (uo == "under"){
                if (runner.ex.get.availableToLay.toList(0).price <= alarmOdds){
                  //println(runner.ex.get.availableToBack.toList(0).price)
                  service.alarm = true
                  service.alarmMarket = this
                  playAlarm()
                }
                else{
                  println("under: no alarm", alarmOdds.toString)
                }
              }
              else{
                if (runner.ex.get.availableToBack.toList(0).price >= alarmOdds){
                  //println(runner.ex.get.availableToBack.toList(0).price)
                  service.alarm = true
                  service.alarmMarket = this
                  playAlarm()
                }
                else{
                  println("over: no alarm")
                }
              }
                //service.alarm = true
                //Thread.sleep(20*1000)
                //TODO! piknięcie co 15 sekund + tooltip
            }
          }
        }
      case Success(None) =>
        println("error no result returned")
      case Failure(error) =>
        println("error " + error)
    }
  }

  val system = akka.actor.ActorSystem("system")
  system.scheduler.schedule(5 seconds, 15 seconds)({
    if(alarmDisabled == false){
      checkAlarmMarket()
    }
  })
}

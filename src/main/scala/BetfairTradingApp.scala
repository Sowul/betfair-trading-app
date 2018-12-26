import java.awt.MouseInfo
import java.io._
import java.net.URL
import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.control.Alert
import javafx.scene.control.Alert.AlertType
import javafx.scene.{Parent, Scene}
import javafx.stage.{Stage, StageStyle}

import app.{AlarmMarket, DataSource, LadderInterface}

import scala.concurrent.duration._
import app.controllers.service

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.collection.mutable.ArrayBuffer
import scala.util.{Failure, Success}
import scala.util.control.Breaks.break

object BetfairTradingApp extends App {
  override def main(args: Array[String]) {
    Application.launch(classOf[BetfairTradingApp], args: _*)
  }
}

class BetfairTradingApp extends Application {
  override def start(stage: Stage): Unit = {
    val root: Parent = FXMLLoader.load(getClass().getResource("/fxml/Login.fxml"))
    val scene: Scene = new Scene(root)
    val stage: Stage = new Stage

    stage.setScene(scene)
    //stage.initStyle(StageStyle.UNDECORATED)
    stage.show()
  }



  override def stop(): Unit = {
    scala.util.control.Exception.ignoring(classOf[java.lang.RuntimeException]) {
      if (service.sessionToken.nonEmpty) {
        if (service.closeCheckBox){
          val ladder = new LadderInterface()
          autocloseBets(ladder)
          Thread.sleep(3000)
        }
        //TODO! tu otworzyć i przepisać rynki do

        //println("im in")

        val fw = new PrintWriter("my_markets.txt")
        for (item <- DataSource.data) {
          println("in the loop")
          service.favouriteHashMap(item.marketName, item.alarmOdds)
          println("key exists")
          if(service.favouriteHashMap isDefinedAt((item.marketName, item.alarmOdds))){
            //println("tak")
            //println(service.favouriteHashMap(item.marketName))
            val m = service.favouriteHashMap(item.marketName, item.alarmOdds)
            //println(m)
            val content = m.marketName + "," + m.marketFullName + "," + m.marketId + "," + m.runnerId.toString + "," + m.alarmOdds.toString + "," + service.myMarketsHashMap(m.marketFullName).eventName + "," + service.myMarketsHashMap(m.marketFullName).marketName + "," + m.uo
            //println(content)
            fw.println(content)
          }
          else{
            //println("nie ma klucza")
          }
        }
        //println("before fw close")
        fw.close()
        //println("after fw close")


        service.betfairServiceNG.logout(service.sessionToken)
        println("logged out")
      }
      else {
        println("empty token")
        println("not logged in so not logged out")
      }
      println("exit")
      super.stop()
      System.exit(0)
    }
  }

  def autocloseBets(ladder: LadderInterface): Unit = {
    println("Bets will be autoclosed on exit.")
    scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
      service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
        case Success(Some(currentOrderSummaryReportContainer)) =>
          for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
            println(currentOrderSummary)
            ladder.cancelBet(currentOrderSummary.marketId, currentOrderSummary.betId, Some(0D))
          }
        case Success(None) =>
          println("error no result returned")
        case Failure(error) =>
          println("error " + error)
      }
    }
  }
}
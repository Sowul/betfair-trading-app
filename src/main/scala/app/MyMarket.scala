package app

import javafx.beans.property.{SimpleStringProperty}

/**
  * domain object
  */
case class MyMarket(marketName: String, alarmOdds: String)

/**
  * domain object, but usable with javafx
  */
class MutableMyMarket {

  val marketNameProperty: SimpleStringProperty = new SimpleStringProperty()
  val alarmOddsProperty: SimpleStringProperty = new SimpleStringProperty()

  def setMarketName(marketName: String) = marketNameProperty.set(marketName)

  def setAlarmOdds(alarmOdds: String) = alarmOddsProperty.set(alarmOdds)
}

/**
  * companion object to get a better initialisation story
  */
object MutableMyMarket {

  def apply(a: MyMarket): MutableMyMarket = {
    val ma = new MutableMyMarket
    ma.setMarketName(a.marketName)
    ma.setAlarmOdds(a.alarmOdds)
    ma
  }

}
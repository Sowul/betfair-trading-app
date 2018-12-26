package app

import javafx.beans.property.{SimpleDoubleProperty, SimpleStringProperty}

/**
  * domain object
  */
case class Ladder(hedgePart: String = "", hedge: String = "",
                  myBack: String = "", back: String = "",
                  odds: Double,
                  lay: String = "", myLay: String = "",
                  vol: String = "")

/**
  * domain object, but usable with javafx
  */
class MutableLadder {

  val hedgePartProperty: SimpleStringProperty = new SimpleStringProperty()
  val hedgeProperty: SimpleStringProperty = new SimpleStringProperty()
  val myBackProperty: SimpleStringProperty = new SimpleStringProperty()
  val backProperty: SimpleStringProperty = new SimpleStringProperty()
  val oddsProperty: SimpleDoubleProperty = new SimpleDoubleProperty()
  val layProperty: SimpleStringProperty = new SimpleStringProperty()
  val myLayProperty: SimpleStringProperty = new SimpleStringProperty()
  val volProperty: SimpleStringProperty = new SimpleStringProperty()

  def setHedgePart(hedgePart: String) = hedgePartProperty.set(hedgePart)
  def setHedge(hedge: String) = hedgeProperty.set(hedge)
  def setMyBack(myBack: String) = myBackProperty.set(myBack)
  def setBack(back: String) = backProperty.set(back)
  def setOdds(odds: Double) = oddsProperty.set(odds)
  def setLay(lay: String) = layProperty.set(lay)
  def setMyLay(myLay: String) = myLayProperty.set(myLay)
  def setVol(vol: String) = volProperty.set(vol)

  //podzielić kolumny na zupełnie oddzielne klasy?

}

/**
  * companion object to get a better initialisation story
  */
object MutableLadder {

  def apply(a: Ladder): MutableLadder = {
    val ma = new MutableLadder
    ma.setHedgePart(a.hedgePart)
    ma.setHedge(a.hedge)
    ma.setMyBack(a.myBack)
    ma.setBack(a.back)
    ma.setOdds(a.odds)
    ma.setLay(a.lay)
    ma.setMyLay(a.myLay)
    ma.setVol(a.vol)
    ma
  }

}
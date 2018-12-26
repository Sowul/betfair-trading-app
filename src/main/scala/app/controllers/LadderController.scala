package app.controllers

import java.awt.Toolkit

import scala.concurrent.duration._
import java.io.{BufferedReader, InputStreamReader}
import java.lang.reflect.InvocationTargetException
import java.net.URL
import java.util.{Optional, ResourceBundle}
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.fxml.{FXML, FXMLLoader, Initializable}
import javafx.scene.control._
import javafx.scene.layout.{FlowPane, HBox, VBox}
import javafx.geometry.Orientation
import javafx.scene.input.MouseEvent
import javafx.event.ActionEvent

import app._
import javafx.scene.paint.Color
import javafx.scene.shape.Circle
import javafx.util.Callback

import scala.util.{Failure, Success}
import scala.concurrent._
import ExecutionContext.Implicits.global
import app.JfxUtils._
import com.betfair.domain.{LimitOrder, OrderType, PersistenceType, PlaceInstruction, PriceData, PriceProjection, Side}
import com.typesafe.config.ConfigFactory

import scala.collection.mutable.ArrayBuffer
import java.io.File
import javafx.scene.media.{Media, MediaPlayer}

import com.jfoenix.controls.JFXCheckBox

/**
  * Created by backrus on 05/10/16.
  */
class LadderController extends Initializable {

  type LadderTC[T] = TableColumn[MutableLadder, T]

  @FXML var ladder: TableView[MutableLadder] = _

  @FXML var hpCol: LadderTC[String] = _
  @FXML var hCol: LadderTC[String] = _
  @FXML var mybCol: LadderTC[String] = _
  @FXML var bCol: LadderTC[String] = _
  @FXML var oddsCol: LadderTC[Double] = _
  @FXML var lCol: LadderTC[String] = _
  @FXML var mylCol: LadderTC[String] = _
  @FXML var volCol: LadderTC[String] = _

  @FXML var randCB: JFXCheckBox = _
  @FXML var stakeTF: TextField = _
  @FXML var randTF: TextField = _

  @FXML var stakeTB: ToolBar = _
  @FXML var stakeBtnBar: FlowPane = _
  @FXML var plusStakeBtn: Button = _

  @FXML var hpTB: ToolBar = _
  @FXML var hpBtnBar: HBox = _
  @FXML var plusHpBtn: Button = _

  @FXML var pingIndicator: Circle = _
  @FXML var alarmIndicator: Circle = _

  var stakes = ArrayBuffer[String]()
  var stakesToggleGroup = new ToggleGroup()
  var hps = ArrayBuffer[String]()
  var hpsToggleGroup = new ToggleGroup()

  def testo(): Unit = {}

  var pinged: Boolean = false

  def ping(): Unit = {
    val command: String = "nmap --unprivileged -sT -Pn -p 443 api.betfair.com"
    val proc: Process = Runtime.getRuntime().exec(command)
    var line: String = ""
    val in: BufferedReader = new BufferedReader(new InputStreamReader(proc.getInputStream()))
    while ((in.readLine()) != null) {
      line = in.readLine()
    }
    val result = line
    in.close()
    if (pinged == false) {
      service.ping = result.split(" ")(10).toDouble
      pinged = true
      if (service.ping < 0.90) {
        pingIndicator.setFill(Color.LIGHTGREEN)
      }
    }
    if ((result.split(" ")(10).toDouble) >= service.ping * 3) {
      pingIndicator.setFill(Color.RED)
    }
    else {
      pingIndicator.setFill(Color.LIGHTGREEN)
    }
  }

  val system = akka.actor.ActorSystem("system")
  system.scheduler.schedule(0 seconds, 1 seconds)({
    ping()
  })

  def checkAlarm(): Unit ={
    scala.util.control.Exception.ignoring(classOf[java.lang.NullPointerException]){
      scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]){
        if(service.alarm == true){
          println("service.alarm.t", service.alarm)
          alarmIndicator.setFill(Color.YELLOW)
          val t: Tooltip = new Tooltip(service.alarmMarket.marketFullName)
          Tooltip.install(alarmIndicator, t)
          t.show(alarmIndicator.getScene.getWindow)
          println("yellow")
        }
        else{
          println("service.alarm.f", service.alarm)
          alarmIndicator.setFill(Color.WHITE)
          println("white")
        }
      }
    }
  }
  val alrm = akka.actor.ActorSystem("alrm")
  alrm.scheduler.schedule(0 seconds, 15 seconds)({
    checkAlarm()
  })

  def addPlusBtn(): Unit ={
    var btn = new Button("+")
    btn.setPrefWidth(15)
    btn.setPrefHeight(20)
    btn.setMinWidth(15)
    btn.setMinHeight(20)
    btn.setStyle("-fx-padding: 0.1em")
    btn.setOnAction(new EventHandler[ActionEvent]() {
      override def handle(event: ActionEvent): Unit = {
        addStakeBtn()
      }
    })
    stakeBtnBar.getChildren.add(btn)
    val separator = new Separator()
    separator.setOrientation(Orientation.VERTICAL)
    separator.setMinWidth(1)
    stakeBtnBar.getChildren.add(separator)
  }

  def addStakeBtn(): Unit = {
    val dialog: TextInputDialog = new TextInputDialog("")
    dialog.setTitle("")
    dialog.setHeaderText("")
    dialog.setContentText("Set button stake:")

    val stake: Optional[String] = dialog.showAndWait()
    if (stake.isPresent){
      stakes += stake.get()
      stakeBtnBar.getChildren.clear()
      stakeTB.getItems.remove(0)
      addPlusBtn()
      for (x <- stakes){
        val btn = new ToggleButton(x)
        btn.setOnAction(new EventHandler[ActionEvent]() {
          override def handle(event: ActionEvent): Unit = {
            if (btn.getText.contains("k")){
              service.stake = btn.getText.split("k")(0).toDouble*1000
            }
            else {
              btn.setMinWidth(20)
              service.stake = btn.getText.toDouble
            }
            stakeTF.setText(service.stake.toString)
          }
        })
        stakesToggleGroup.getToggles.add(btn)
        stakeBtnBar.getChildren.add(btn)
        val separator = new Separator()
        separator.setOrientation(Orientation.VERTICAL)
        stakeBtnBar.getChildren.add(separator)
      }
      stakeTB.getItems.add(stakeBtnBar)
    }
  }

  def addHpBtn(): Unit = {
    val dialog: TextInputDialog = new TextInputDialog("")
    dialog.setTitle("")
    dialog.setHeaderText("")
    dialog.setContentText("Set partial hedge %:")

    val hp: Optional[String] = dialog.showAndWait()
    if (hp.isPresent) {
      hps += hp.get()
      hpBtnBar.getChildren.clear()
      hpTB.getItems.remove(1)
      for (x <- hps) {
        val btn = new ToggleButton(x)
        btn.setOnAction(new EventHandler[ActionEvent]() {
          override def handle(event: ActionEvent): Unit = {
            if (btn.isSelected){
              service.hedgePartial = btn.getText.toDouble/100
            }
            else{
              service.hedgePartial = 1
            }
            println(service.hedgePartial)
          }
        })
        hpsToggleGroup.getToggles.add(btn)
        hpBtnBar.getChildren.add(btn)
        val separator = new Separator()
        separator.setOrientation(Orientation.VERTICAL)
        hpBtnBar.getChildren.add(separator)
      }
      hpTB.getItems.add(hpBtnBar)
    }
  }

  def loadButtons(): Unit ={
    val conf = ConfigFactory.load("buttons")
    //TODO! odkomentować do kompilacji
    //val conf = ConfigFactory.parseFile(new File("buttons.conf"))
    val stakeButtons = conf.getString("stakeButtons")
    val hpButtons = conf.getString("hpButtons")

    for (x <- stakeButtons.split(",")) {
      stakes += x
    }
    stakeBtnBar.getChildren.clear()
    stakeTB.getItems.remove(0)

    addPlusBtn()

    for (x <- stakes) {
      var btn = new ToggleButton(x)
      btn.setMinWidth(50)
      btn.setOnAction(new EventHandler[ActionEvent]() {
        override def handle(event: ActionEvent): Unit = {
          if (btn.getText.contains("k")){
            service.stake = btn.getText.split("k")(0).toDouble*1000
          }
          else {
            btn.setMinWidth(20)
            service.stake = btn.getText.toDouble
          }
          stakeTF.setText(service.stake.toString)
        }
      })
      stakesToggleGroup.getToggles.add(btn)
      stakeBtnBar.getChildren.add(btn)
      val separator = new Separator()
      separator.setOrientation(Orientation.VERTICAL)
      separator.setMinWidth(1)
      stakeBtnBar.getChildren.add(separator)
    }

    stakeTB.getItems.add(stakeBtnBar)

    for (x <- hpButtons.split(",")) {
      hps += x
    }
    hpBtnBar.getChildren.clear()
    hpTB.getItems.remove(1)
    for (x <- hps) {
      val btn = new ToggleButton(x)
      btn.setOnAction(new EventHandler[ActionEvent]() {
        override def handle(event: ActionEvent): Unit = {
          if (btn.isSelected){
            service.hedgePartial = btn.getText.toDouble/100
          }
          else{
            service.hedgePartial = 1
          }
          println(service.hedgePartial)
        }
      })
      hpsToggleGroup.getToggles.add(btn)
      hpBtnBar.getChildren.add(btn)
      val separator = new Separator()
      separator.setOrientation(Orientation.VERTICAL)
      hpBtnBar.getChildren.add(separator)
    }
    hpTB.getItems.add(hpBtnBar)
  }

  /**
    * provide a table column and a generator function for the value to put into
    * the column.
    *
    * @tparam T the type which is contained in the property
    * @return
    */
  def initTableViewColumn[T]: (LadderTC[T], (MutableLadder) => Any) => Unit = {
    initTableViewColumnCellValueFactory[MutableLadder, T]
  }

  override def initialize(location: URL, resources: ResourceBundle): Unit = {

    initTableViewColumn[String](hpCol, _.hedgePartProperty)
    initTableViewColumn[String](hCol, _.hedgeProperty)
    initTableViewColumn[String](mybCol, _.myBackProperty)
    initTableViewColumn[String](bCol, _.backProperty)
    initTableViewColumn[Double](oddsCol, _.oddsProperty)
    initTableViewColumn[String](lCol, _.layProperty)
    initTableViewColumn[String](mylCol, _.myLayProperty)
    initTableViewColumn[String](volCol, _.volProperty)

    bCol.setCellFactory(new Callback[TableColumn[MutableLadder, String], TableCell[MutableLadder, String]]() {
      override def call(p: TableColumn[MutableLadder, String]): TableCell[MutableLadder, String] = {
        new TableCell[MutableLadder, String] {
          override def updateItem(item: String, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty || item == "") {
              setStyle("-fx-background-color: rgb(197, 238, 237)")
            }else{
              setStyle("-fx-background-color: rgb(157, 201, 220)")
            }
            setText(item)
          }
        }
      }
    })

    lCol.setCellFactory(new Callback[TableColumn[MutableLadder, String], TableCell[MutableLadder, String]]() {
      override def call(p: TableColumn[MutableLadder, String]): TableCell[MutableLadder, String] = {
        new TableCell[MutableLadder, String] {
          override def updateItem(item: String, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty || item == "") {
              setStyle("-fx-background-color: rgb(255, 202, 213)")
            }else{
              setStyle("-fx-background-color: rgb(235, 158, 181)")
            }
            setText(item)
          }
        }
      }
    })
    //
    //lCol.setCellFactory()
    //volCol.setCellFactory()
    //ping()

    loadButtons()

    alarmIndicator.setOnMouseClicked(new EventHandler[MouseEvent]() {
      override def handle(event: MouseEvent): Unit = {
        service.alarmMarket.alarmDisabled = true
        service.alarm = false
        //service.alarmMarket = _
      }
    })

  }
}


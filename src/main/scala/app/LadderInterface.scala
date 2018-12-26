package app

import javafx.application.Platform
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.collections.ObservableList
import javafx.event.{ActionEvent, Event, EventHandler}
import javafx.fxml.FXMLLoader
import javafx.scene.{Parent, Scene}
import javafx.scene.control._
import javafx.scene.input._
import javafx.scene.layout.VBox
import javafx.stage.{Stage, WindowEvent}

import akka.actor.Props
import app.JfxUtils.mkObservableList
import app.controllers.service
import com.betfair.domain.Side.Side
import com.betfair.domain._
import com.jfoenix.controls.{JFXCheckBox, JFXComboBox}
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter, ISODateTimeFormat}

import scala.util.{Failure, Random, Success}
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.collection.mutable.HashMap
import scala.util.control.Breaks._

/**
  * Created by backrus on 04/12/16.
  */
class LadderInterface() {


  /*def dockLadder(): Unit ={
    import com.sun.javafx.stage.StageHelper
    val stage = StageHelper.getStages().get(0)
    if(ltp.isExpanded){
      ltp.setExpanded(false)
      stage.setMinHeight(56)
      stage.setHeight(56)
      stage.setAlwaysOnTop(true)
    }
    else{
      ltp.setExpanded(true)
      stage.setHeight(service.screenHeight-50)
      stage.setAlwaysOnTop(false)
    }
  }*/


  val minStake = 4

  var delay: Int = 0
  var inPlay: Boolean = false
  var totalMatched: String = _
  var totalMatchedOnRnr: String = _

  //var hedging: Boolean = false
  var opened: Boolean = false
  var notClicked: Boolean = true
  val tabsStage: Stage = new Stage
  tabsStage.setOnCloseRequest(new EventHandler[WindowEvent]() {
    def handle(we: WindowEvent) {
      //println("zamykam")
      tabsStage.hide()
    }
  })
  val tabsRoot: Parent = FXMLLoader.load(getClass().getResource("/fxml/Tabs.fxml"))
  val tabPane = tabsRoot.asInstanceOf[TabPane]

  val newTabsStage: Stage = new Stage
  newTabsStage.setOnCloseRequest(new EventHandler[WindowEvent]() {
    def handle(we: WindowEvent) {
      //println("zamykam")
      newTabsStage.hide()
    }
  })

  val newTabsRoot: Parent = FXMLLoader.load(getClass().getResource("/fxml/Tabs.fxml"))
  val newTabPane = newTabsRoot.asInstanceOf[TabPane]


  def openMarket(item: TreeItem[Object], key: MarketHashMapKey, name: String, tab: Tab) {
    //println("Is leaf?: ", item.isLeaf)
    //println("opened?: ", opened)
    if (item.isLeaf) {

      tab.setOnClosed(new EventHandler[Event] {
        def handle(event: Event): Unit = {
          //println("TAB CLOSING...")
          service.openedSet -= tab.getText
        }
      })

      //println("Nic jeszcze nie otworzono")
      //println("opened == false -> NIE otwarto")
      //println(name.slice(12, name.length))
      //println("opened == false -> openedSet:", openedSet)
      //import app.MutableLadder

      //val ltp: TitledPane = FXMLLoader.load(getClass().getResource("/fxml/Ladder.fxml"))
      val vbox: VBox = FXMLLoader.load(getClass().getResource("/fxml/Ladder.fxml"))
      //val vbox: VBox = ltp.getContent.asInstanceOf[VBox]
      import javafx.scene.layout.HBox
      var hbox: HBox = vbox.getChildren.get(0).asInstanceOf[HBox]
      var selectionCB: JFXComboBox[String] = hbox.getChildren.get(0).asInstanceOf[JFXComboBox[String]]
      //dać je na globala?
      val stackCB: JFXCheckBox = hbox.getChildren.get(1).asInstanceOf[JFXCheckBox]
      val stakeTF: TextField = hbox.getChildren.get(2).asInstanceOf[TextField]
      val randTF: TextField = hbox.getChildren.get(3).asInstanceOf[TextField]

      val tb: ToolBar = vbox.getChildren.get(4).asInstanceOf[ToolBar]
      val hb: HBox = tb.getItems.get(0).asInstanceOf[HBox]
      val sndLadderBtn: Button = hb.getChildren.get(0).asInstanceOf[Button]

      sndLadderBtn.setOnAction(new EventHandler[ActionEvent]() {
        override def handle(event: ActionEvent): Unit = {
          //zablokować ponowne dodanie rynku, prawdopodobnie następna hashmapa
          //println("open 2nd ladder")
          notClicked = false
          openLadder(item, key, name)
        }
      })

      val ordersHB: HBox = vbox.getChildren.get(5).asInstanceOf[HBox]
      var pendingSP: ScrollPane = ordersHB.getChildren().get(0).asInstanceOf[ScrollPane]
      var matchedSP: ScrollPane = ordersHB.getChildren().get(1).asInstanceOf[ScrollPane]
      var pendingVB: VBox = pendingSP.getContent.asInstanceOf[VBox]
      var matchedVB: VBox = matchedSP.getContent.asInstanceOf[VBox]

      val runners = service.teamsHashMap(key)
      for (runner <- runners) {
        selectionCB.getItems().add(runner.runnerName)
        service.runnersHashMap += (runner.runnerName -> runner.selectionId)
        //println(runner.selectionId)
      }

      val ladderTableView = vbox.getChildren.get(3).asInstanceOf[TableView[MutableLadder]]
      ladderTableView.getSelectionModel().setCellSelectionEnabled(true)
      var ladder = mkObservableList(DataSource.ladder.reverse.map(MutableLadder(_)))
      val emptyLadder = mkObservableList(DataSource.ladder.reverse.map(MutableLadder(_)))

      var selectionId = ""
      selectionCB.getSelectionModel().selectedItemProperty()
        .addListener(new ChangeListener[String] {

          def changed(observable: ObservableValue[_ <: String], oldValue: String, newValue: String): Unit = {
            //printuje tyle razy ile kliknieto, ale przy prownaniu siedzi jedna wartosc
            for (i <- 0 to 349) {
              ladder.get(i).setVol("")
              ladder.get(i).setBack("")
              ladder.get(i).setLay("")
              ladder.get(i).setHedge("H")
              ladder.get(i).setHedgePart("HP")
            }
            //println(oldValue, newValue)
            //selectionCB.getSelectionModel().select(0)
            import akka.actor.ActorSystem
            selectionId = selectionCB.getSelectionModel.getSelectedItem
            Thread.sleep(1000)
            refreshMarket(ladderTableView, ladder, key, selectionCB.getSelectionModel.getSelectedItem)

            //autocenter ladder
            Thread.sleep(1000)
            breakable {
              for (i <- 0 to 349) {
                if (ladderTableView.getColumns.get(3).getCellData(i) != "") {
                  Platform.runLater(new Runnable() {
                    override def run() {
                      ladderTableView.scrollTo(i - 10)
                    }
                  })
                  break
                }
                else {
                  Platform.runLater(new Runnable() {
                    override def run() {
                      ladderTableView.scrollTo(349)
                    }
                  })
                }
              }
            }
            //print("1")

            import scala.concurrent.duration._
            import scala.concurrent.ExecutionContext
            import ExecutionContext.Implicits.global
            val system = akka.actor.ActorSystem("system")
            system.scheduler.schedule(5 seconds, 201 milliseconds)({
              refreshMarket(ladderTableView, ladder, key, selectionCB.getSelectionModel.getSelectedItem)
            })
            val orders = ActorSystem()
            orders.scheduler.schedule(5 seconds, 201 milliseconds)({
              refreshMyOrders(ladderTableView, ladder, key, selectionCB.getSelectionModel.getSelectedItem)
            })
            /*if (tab.isSelected) {
              //println(name, "aktywna")
              system.scheduler.schedule(0 seconds, 201 milliseconds)({
                refreshMarket(ladderTableView, ladder, key, selectionCB.getSelectionModel.getSelectedItem)
              })
            } else {
              //println(name, "nieaktywna")
              system.scheduler.schedule(0 seconds, 201 milliseconds)({
                refreshMarket(ladderTableView, ladder, key, selectionCB.getSelectionModel.getSelectedItem)
              })
            }*/
            //TODO! currentOrders - skolorkować i cancelBet
            val context = ActorSystem()
            val javaFxActor = context.actorOf(Props.empty.withDispatcher("javafx-dispatcher"), "javaFxActor")
            //201
            context.scheduler.schedule(5 seconds, 201 milliseconds)({
              scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
                service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
                  //TODO! kolorki i format, sprawdzić, co się będzie dobrze klikało
                  case Success(Some(currentOrderSummaryReportContainer)) =>
                    Platform.runLater(new Runnable() {
                      override def run(): Unit = {
                        pendingVB.getChildren.clear()
                        matchedVB.getChildren.clear()
                        for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
                          if (currentOrderSummary.selectionId == service.runnersHashMap(selectionId)) {
                            if (currentOrderSummary.status == OrderStatus.EXECUTABLE) {
                              /*val order = new Label(currentOrderSummary.priceSize.price.toString+"\t"+"\t"+"\t"+"\t"+
                                currentOrderSummary.priceSize.size.toString)
                              order.setOnMouseClicked(new EventHandler[MouseEvent]() {
                                override def handle(event: MouseEvent): Unit = {
                                  cancelBet(currentOrderSummary.marketId, currentOrderSummary.betId)
                                  println("testo-cancel")
                                }
                              }*/

                              //java.lang.IllegalArgumentException
                              val txt = currentOrderSummary.priceSize.price.toString+"                    "+
                                currentOrderSummary.priceSize.size.toString
                              val order = new TextField(txt)
                              order.setOnMouseClicked(new EventHandler[MouseEvent]() {
                                override def handle(event: MouseEvent): Unit = {
                                  scala.util.control.Exception.ignoring(classOf[java.lang.IllegalArgumentException]) {
                                    cancelBet(currentOrderSummary.marketId, currentOrderSummary.betId)
                                    println("testo-cancel")
                                  }
                                }
                              })
                              order.setEditable(false)
                              order.setPrefWidth(pendingVB.getWidth)
                              if (currentOrderSummary.side == Side.LAY) {
                                order.setStyle("-fx-background-color: rgb(255, 202, 213);" +
                                  "-fx-border-color: gray;" +
                                  "-fx-border-width: 1 1 1 1")
                              }
                              else {
                                order.setStyle("-fx-background-color: rgb(197, 238, 237)" +
                                  "-fx-border-color: gray;" +
                                  "-fx-border-width: 1 1 1 1")
                              }
                              pendingVB.getChildren.add(order)
                            }
                            else if (currentOrderSummary.status == OrderStatus.EXECUTION_COMPLETE) {
                              /*val order = new Label(currentOrderSummary.priceSize.price.toString+"\t"+"\t"+"\t"+"\t"+
                                currentOrderSummary.priceSize.size.toString)*/
                              val txt = currentOrderSummary.priceSize.price.toString+"                    "+
                                currentOrderSummary.priceSize.size.toString
                              val order = new TextField(txt)
                              order.setEditable(false)
                              order.setPrefWidth(matchedVB.getWidth)
                              if (currentOrderSummary.side == Side.LAY) {
                                order.setStyle("-fx-background-color: rgb(235, 158, 181)" +
                                  "-fx-border-color: gray;" +
                                  "-fx-border-width: 1 1 1 1")
                              }
                              else {
                                order.setStyle("-fx-background-color: rgb(157, 201, 220)" +
                                  "-fx-border-color: gray;" +
                                  "-fx-border-width: 1 1 1 1")
                              }
                              matchedVB.getChildren.add(order)
                            }
                          }
                        }
                        pendingSP.setContent(pendingVB)
                        matchedSP.setContent(matchedVB)
                      }
                    })
                  //println("Order is: " + currentOrderSummary)
                  case Success(None) =>
                    println("error no result returned")
                  case Failure(error) =>
                    println("error " + error)
                }
              }
            })
          }
        })

      val colHeaderMyLTextField = new TextField("MyL")
      colHeaderMyLTextField.setOnMouseClicked(new EventHandler[MouseEvent]() {
        override def handle(event: MouseEvent): Unit = {
          scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
            service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
              case Success(Some(currentOrderSummaryReportContainer)) =>
                for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
                  if (currentOrderSummary.selectionId == service.runnersHashMap(selectionId)) {
                    if (currentOrderSummary.side == Side.LAY) {
                      if (currentOrderSummary.status == OrderStatus.EXECUTABLE){
                        cancelBet(currentOrderSummary.marketId, currentOrderSummary.betId)
                      }
                    }
                  }
                }
              case Success(None) =>
                println("error no result returned")
              case Failure(error) =>
                println("error " + error)
            }
          }
        }
      })
      colHeaderMyLTextField.setEditable(false)
      ladderTableView.getColumns.get(6).setGraphic(colHeaderMyLTextField)

      val colHeaderMyBTextField = new TextField("MyB")
      colHeaderMyBTextField.setOnMouseClicked(new EventHandler[MouseEvent]() {
        override def handle(event: MouseEvent): Unit = {
          scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
            service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
              case Success(Some(currentOrderSummaryReportContainer)) =>
                for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
                  if (currentOrderSummary.selectionId == service.runnersHashMap(selectionId)) {
                    if (currentOrderSummary.side == Side.BACK) {
                      if (currentOrderSummary.status == OrderStatus.EXECUTABLE){
                        cancelBet(currentOrderSummary.marketId, currentOrderSummary.betId)
                      }
                    }
                  }
                }
              case Success(None) =>
                println("error no result returned")
              case Failure(error) =>
                println("error " + error)
            }
          }
        }
      })
      colHeaderMyBTextField.setEditable(false)
      ladderTableView.getColumns.get(2).setGraphic(colHeaderMyBTextField)

      ladderTableView.setOnMouseClicked(new EventHandler[MouseEvent]() {
        override def handle(event: MouseEvent): Unit = {
          scala.util.control.Exception.ignoring(classOf[java.lang.IndexOutOfBoundsException]) {
            if (event.getButton().equals(MouseButton.PRIMARY)) {
              val selectedCells: ObservableList[TablePosition[_, _]] = ladderTableView.getSelectionModel().getSelectedCells()
              val tablePosition = selectedCells.get(0)

              //TODO! getOdds
              val cell = event.getPickResult.getIntersectedNode.getParent.getChildrenUnmodifiable
              val odds = cell.get(4).toString.split("'")(1)

              //TODO! getStake
              val value = tablePosition.getTableColumn().getCellData(tablePosition.getRow())
              var stake = 0.0
              var rand = 0.0
              if (randTF.getText != "") {
                if (randTF.getText.toDouble > stakeTF.getText.toDouble) {
                  rand = stakeTF.getText.toInt
                }
                else {
                  rand = ((Random.nextDouble() - 0.5) * 2 * (randTF.getText.toDouble)).toInt
                }
              }

              //TODO! setStake
              if (stackCB.isSelected) {
                if (stakeTF.getText != "") {
                  stake = stakeTF.getText.toDouble + rand
                }
              }
              else {
                stake = service.stake
              }

              //TODO! placeBet LAY
              if (tablePosition.getColumn() == 6) {
                if (stake > 0){
                  if (stake < minStake){
                    val placeInstructions = Set(PlaceInstruction(orderType = OrderType.LIMIT, service.runnersHashMap(selectionId).toInt,
                      handicap = Some(0.0), Side.LAY,
                      limitOrder = Some(LimitOrder(((minStake+stake*100).round/100.toDouble), 1.01D, persistenceType = PersistenceType.PERSIST))))
                    scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]){
                      service.betfairServiceNG.placeOrders(service.sessionToken, service.marketHashMap(key), instructions = placeInstructions
                      ) onComplete {
                        case Success(Some(placeExecutionReportContainer)) =>
                          //println(placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId)
                          cancelBet(service.marketHashMap(key), placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get, Some(minStake))
                          //Thread.sleep(200)
                          replaceBet(service.marketHashMap(key), placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get, odds.toDouble)
                        case Success(None) =>
                          println("error no result returned")
                        case Failure(error) =>
                          println("error " + error)
                      }
                    }
                  }
                  else {
                    scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]){
                      placeBet(service.marketHashMap(key), service.runnersHashMap(selectionId).toInt, ((stake*100).round/100.toDouble), odds.toDouble, Side.LAY)
                    }
                  }
                }
              }
              //TODO! placeBet BACK
              else if (tablePosition.getColumn() == 2) {
                if (stake > 0){
                  if (stake < minStake){
                    val placeInstructions = Set(PlaceInstruction(orderType = OrderType.LIMIT, service.runnersHashMap(selectionId).toInt,
                      handicap = Some(0.0), Side.BACK,
                      limitOrder = Some(LimitOrder((((minStake + stake)*100).round/100.toDouble), 1000D, persistenceType = PersistenceType.PERSIST))))
                    scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]){
                      service.betfairServiceNG.placeOrders(service.sessionToken, service.marketHashMap(key), instructions = placeInstructions
                      ) onComplete {
                        case Success(Some(placeExecutionReportContainer)) =>
                          //println(placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId)
                          cancelBet(service.marketHashMap(key), placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get, Some(minStake))
                          //Thread.sleep(200)
                          replaceBet(service.marketHashMap(key), placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get, odds.toDouble)
                        case Success(None) =>
                          println("error no result returned")
                        case Failure(error) =>
                          println("error " + error)
                      }
                    }
                  }
                  else {
                    scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]){
                      placeBet(service.marketHashMap(key), service.runnersHashMap(selectionId).toInt, ((stake*100).round/100.toDouble), odds.toDouble, Side.BACK)
                    }
                  }
                }
              }
              //TODO! hedge
              else if (tablePosition.getColumn() == 0 || tablePosition.getColumn() == 1){
                //TODO! NIE KASUJEMY
                /*scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
                  service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
                    case Success(Some(currentOrderSummaryReportContainer)) =>
                      for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
                        if (currentOrderSummary.selectionId == service.runnersHashMap(selectionId)) {
                          if (currentOrderSummary.status == OrderStatus.EXECUTABLE) {
                            println("Kasujemy")
                            cancelBet(currentOrderSummary.marketId, currentOrderSummary.betId)
                          }
                        }
                      }
                    case Success(None) =>
                      println("error no result returned")
                    case Failure(error) =>
                      println("error " + error)
                  }
                }*/
                var laysTotalPayout = 0D
                var backsTotalPayout = 0D
                scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
                  service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
                    case Success(Some(currentOrderSummaryReportContainer)) =>
                      for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
                        if (currentOrderSummary.selectionId == service.runnersHashMap(selectionId)) {
                          if (currentOrderSummary.status == OrderStatus.EXECUTION_COMPLETE) {
                            if (currentOrderSummary.side == Side.LAY) {
                              laysTotalPayout += currentOrderSummary.priceSize.price * currentOrderSummary.priceSize.size
                            }
                            else {
                              backsTotalPayout += currentOrderSummary.priceSize.price * currentOrderSummary.priceSize.size
                            }
                          }
                        }
                      }
                      var hedgeStake = 0D
                      if (tablePosition.getColumn() == 1) {
                        hedgeStake = ((Math.abs(laysTotalPayout - backsTotalPayout) / odds.toDouble) * 100).round / 100.toDouble
                      }
                      else {
                        hedgeStake = ((Math.abs(laysTotalPayout - backsTotalPayout) / odds.toDouble) * service.hedgePartial * 100).round / 100.toDouble
                      }
                      println(hedgeStake)
                      var betId = ""

                      if (laysTotalPayout < backsTotalPayout) {
                        if (hedgeStake < minStake) {
                          //println(service.sessionToken)
                          //println(service.marketHashMap(key))
                          //println(service.runnersHashMap(selectionId))
                          val placeInstructions = Set(PlaceInstruction(orderType = OrderType.LIMIT, service.runnersHashMap(selectionId).toInt,
                            handicap = Some(0.0), Side.LAY,
                            limitOrder = Some(LimitOrder((((minStake + hedgeStake)*100).round/100.toDouble), 1.01D, persistenceType = PersistenceType.PERSIST))))
                          scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]){
                            service.betfairServiceNG.placeOrders(service.sessionToken, service.marketHashMap(key), instructions = placeInstructions) onComplete {
                              case Success(Some(placeExecutionReportContainer)) =>
                                println(placeExecutionReportContainer.result)
                                //println("tu nie trybi")
                                //println(placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get)
                                //betId = placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get
                                //println(1)
                                cancelBet(service.marketHashMap(key), placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get, Some(minStake))
                                //println(2)
                                //Thread.sleep(200)
                                replaceBet(service.marketHashMap(key), placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get, odds.toDouble)
                              //println(3)
                              case Success(None) =>
                                println("error no result returned")
                              case Failure(error) =>
                                println("error " + error)
                            }
                          }
                        }
                        else {
                          scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]){
                            placeBet(service.marketHashMap(key), service.runnersHashMap(selectionId).toInt, ((hedgeStake*100).round/100.toDouble), odds.toDouble, Side.LAY)
                          }
                        }
                      }
                      else if (laysTotalPayout > backsTotalPayout) {
                        if (hedgeStake < minStake) {
                          val placeInstructions = Set(PlaceInstruction(orderType = OrderType.LIMIT, service.runnersHashMap(selectionId).toInt,
                            handicap = Some(0.0), Side.BACK,
                            limitOrder = Some(LimitOrder((((minStake + hedgeStake)*100).round/100.toDouble), 1000D, persistenceType = PersistenceType.PERSIST))))
                          scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]){
                            service.betfairServiceNG.placeOrders(service.sessionToken, service.marketHashMap(key), instructions = placeInstructions
                            ) onComplete {
                              case Success(Some(placeExecutionReportContainer)) =>
                                //println(placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId)
                                cancelBet(service.marketHashMap(key), placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get, Some(minStake))
                                //Thread.sleep(200)
                                replaceBet(service.marketHashMap(key), placeExecutionReportContainer.result.instructionReports.toIndexedSeq(0).betId.get, odds.toDouble)
                              case Success(None) =>
                                println("error no result returned")
                              case Failure(error) =>
                                println("error " + error)
                            }
                          }
                        }
                        else {
                          scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]){
                            placeBet(service.marketHashMap(key), service.runnersHashMap(selectionId).toInt, ((hedgeStake*100).round/100.toDouble), odds.toDouble, Side.BACK)
                          }
                        }
                      }
                      else {
                        println("You are hedged.")
                      }

                    case Success(None) =>
                      println("error no result returned")
                    case Failure(error) =>
                      println("error " + error)
                  }
                }
              }
              else {}
            }
          }
        }
      })
      // tu w sumie wszystko proste, bo muszę chwycić tylko oddsy do betId i je zreplacować
      //TODO! cancelBet

      ladderTableView.setOnContextMenuRequested(new EventHandler[ContextMenuEvent]() {
        //val previousOdds = ladderTableView.getColumns.get(4).getCellData(tablePosition.getRow())
        override def handle(event: ContextMenuEvent): Unit = {
          val selectedCells: ObservableList[TablePosition[_, _]] = ladderTableView.getSelectionModel().getSelectedCells()
          val tablePosition = selectedCells.get(0)
          val previousOdds = ladder.get(ladderTableView.getSelectionModel().getSelectedIndex()).oddsProperty.get()
          if (tablePosition.getColumn() == 6) {
            scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
              service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
                case Success(Some(currentOrderSummaryReportContainer)) =>
                  for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
                    if (currentOrderSummary.selectionId == service.runnersHashMap(selectionId)) {
                      //println("price: " ,currentOrderSummary.priceSize.price)
                      //println("odds: " ,previousOdds)
                      if (currentOrderSummary.status == OrderStatus.EXECUTABLE &&
                        currentOrderSummary.side == Side.LAY &&
                        currentOrderSummary.priceSize.price == previousOdds
                      ) {
                        cancelBet(currentOrderSummary.marketId, currentOrderSummary.betId)
                      }
                    }
                  }
                case Success(None) =>
                  println("error no result returned")
                case Failure(error) =>
                  println("error " + error)
              }
            }
          }
          else if (tablePosition.getColumn() == 2) {
            scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
              service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
                case Success(Some(currentOrderSummaryReportContainer)) =>
                  for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
                    if (currentOrderSummary.selectionId == service.runnersHashMap(selectionId)) {
                      if (currentOrderSummary.status == OrderStatus.EXECUTABLE &&
                        currentOrderSummary.side == Side.BACK &&
                        currentOrderSummary.priceSize.price == previousOdds
                      ) {
                        cancelBet(currentOrderSummary.marketId, currentOrderSummary.betId)
                      }
                    }
                  }
                case Success(None) =>
                  println("error no result returned")
                case Failure(error) =>
                  println("error " + error)
              }
            }
          }
          else {}
        }
      })
      //good
      ladderTableView.setOnDragDetected(new EventHandler[MouseEvent]() {
        override def handle(event: MouseEvent): Unit = {
          val db: Dragboard = ladderTableView.startDragAndDrop(TransferMode.MOVE)
          val content: ClipboardContent = new ClipboardContent()
          val selectedCells: ObservableList[TablePosition[_, _]] = ladderTableView.getSelectionModel().getSelectedCells()
          val tablePosition = selectedCells.get(0)
          val value = tablePosition.getTableColumn().getCellData(tablePosition.getRow())
          content.putString(value.toString)
          db.setContent(content)
          event.consume()
        }
      })
      ladderTableView.setOnDragOver(new EventHandler[DragEvent]() {
        override def handle(event: DragEvent): Unit = {
          val db = event.getDragboard
          if (db.hasString()) {
            event.acceptTransferModes(TransferMode.MOVE)
          }
          event.consume()
        }
      })

      //TODO! replaceBet
      ladderTableView.setOnDragDropped(new EventHandler[DragEvent]() {
        override def handle(event: DragEvent) {
          val db = event.getDragboard()
          var success = false
          if (db.hasString()) {
            val cell = event.getPickResult.getIntersectedNode.getParent.getChildrenUnmodifiable
            val odds = cell.get(4).toString.split("'")(1)

            val selectedCells: ObservableList[TablePosition[_, _]] = ladderTableView.getSelectionModel().getSelectedCells()
            val tablePosition = selectedCells.get(0)
            val previousOdds = ladderTableView.getColumns.get(4).getCellData(tablePosition.getRow())

            if (tablePosition.getColumn() == 6) {
              scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
                service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
                  case Success(Some(currentOrderSummaryReportContainer)) =>
                    for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
                      if (currentOrderSummary.selectionId == service.runnersHashMap(selectionId)) {
                        if (currentOrderSummary.side == Side.LAY &&
                          currentOrderSummary.status == OrderStatus.EXECUTABLE &&
                          currentOrderSummary.priceSize.price == previousOdds) {
                          replaceBet(service.marketHashMap(key), currentOrderSummary.betId, odds.toDouble)
                        }
                      }
                    }
                  case Success(None) =>
                    println("error no result returned")
                  case Failure(error) =>
                    println("error " + error)
                }
              }
            }
            else if (tablePosition.getColumn() == 2) {
              scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]) {
                service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
                  case Success(Some(currentOrderSummaryReportContainer)) =>
                    for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
                      if (currentOrderSummary.selectionId == service.runnersHashMap(selectionId)) {
                        if (currentOrderSummary.side == Side.BACK &&
                          currentOrderSummary.status == OrderStatus.EXECUTABLE &&
                          currentOrderSummary.priceSize.price == previousOdds) {
                          replaceBet(service.marketHashMap(key), currentOrderSummary.betId, odds.toDouble)
                        }
                      }
                    }
                  case Success(None) =>
                    println("error no result returned")
                  case Failure(error) =>
                    println("error " + error)
                }
              }
            }
            else {}
          }
          success = true
          event.setDropCompleted(success)
          event.consume()
        }
      })

      vbox.getChildren.remove(3)
      vbox.getChildren.add(3, ladderTableView)
      ladderTableView.setStyle("-fx-font-size: 11")
      ladderTableView.setItems(ladder)
      tab.setContent(vbox)

      if (service.openedSet(name.slice(12, name.length)) && notClicked) {}
      else {
        tab.setText(name.slice(12, name.length))
        service.openedSet += tab.getText

        if (opened == false) {

          //tab.setStyle("-fx-background-color: darkcyan")
          tabPane.getTabs().add(tab)
          tabPane.getSelectionModel.select(tab)
          tabPane.setMinWidth(0.2 * service.screenWidth)
          //tabPane.setPrefHeight(service.screenHeight - 100)
          tabPane.setPrefHeight(0.87*service.screenHeight)

          //tabsStage.setWidth(420)
          tabsStage.setWidth(0.3075*service.screenWidth)
          tabsStage.setMinWidth(0.2 * service.screenWidth)
          //tabsStage.setHeight(service.screenHeight - 50)
          tabsStage.setHeight(0.934*service.screenHeight)

          val ltp = new TitledPane()
          ltp.setOnMouseClicked(new EventHandler[MouseEvent]() {
            override def handle(event: MouseEvent): Unit = {
              if(ltp.isExpanded){
                //ltp.setText(tabPane.getSelectionModel.getSelectedItem.getText)
                ltp.setText(tabPane.getSelectionModel.getSelectedItem.getText + " " + totalMatched)
                ltp.setExpanded(false)
                //tabsStage.setMinHeight(56)
                //tabsStage.setHeight(56)
                tabsStage.setMinHeight(0.073*service.screenHeight)
                tabsStage.setHeight(0.073*service.screenHeight)
                tabsStage.setAlwaysOnTop(true)
              }
              else{
                ltp.setText("")
                ltp.setExpanded(true)
                //tabsStage.setHeight(service.screenHeight-50)
                tabsStage.setHeight(0.934*service.screenHeight)
                tabsStage.setAlwaysOnTop(false)
              }
            }
          })
          ltp.setContent(tabPane)

          //val scene: Scene = new Scene(ltp.asInstanceOf[Parent])
          val scene: Scene = new Scene(ltp)
          tabsStage.setScene(scene)
          //scene.getStylesheets().add(getClass().getResource("/fxml/ladder.css").toExternalForm())
          tabsStage.show()
          opened = true
        }
        else {
          //println("Jestem w elsie")
          //println(opened)

          if (notClicked == false) {
            notClicked = true
            newTabPane.getTabs().add(tab)
            newTabPane.getSelectionModel.select(tab)
            newTabPane.setMinWidth(0.2 * service.screenWidth)
            //newTabPane.setPrefHeight(service.screenHeight - 100)
            newTabPane.setPrefHeight(0.87*service.screenHeight)

            //newTabsStage.setX(service.screenWidth / 2 + 220)
            newTabsStage.setX(service.screenWidth / 2 + 0.1611*service.screenWidth)
            //newTabsStage.setWidth(420)
            newTabsStage.setWidth(0.3075*service.screenWidth)
            newTabsStage.setMinWidth(0.2 * service.screenWidth)
            //newTabsStage.setHeight(service.screenHeight - 50)
            newTabsStage.setHeight(0.934*service.screenHeight)

            val newLtp = new TitledPane()
            newLtp.setOnMouseClicked(new EventHandler[MouseEvent]() {
              override def handle(event: MouseEvent): Unit = {
                if(newLtp.isExpanded){
                  //newLtp.setText(tabPane.getSelectionModel.getSelectedItem.getText)
                  newLtp.setText(tabPane.getSelectionModel.getSelectedItem.getText + " " + totalMatched)
                  newLtp.setExpanded(false)
                  //newTabsStage.setMinHeight(56)
                  //newTabsStage.setHeight(56)
                  newTabsStage.setMinHeight(0.073*service.screenHeight)
                  newTabsStage.setHeight(0.073*service.screenHeight)
                  newTabsStage.setAlwaysOnTop(true)
                }
                else{
                  newLtp.setText("")
                  newLtp.setExpanded(true)
                  //newTabsStage.setHeight(service.screenHeight-50)
                  newTabsStage.setHeight(0.934*service.screenHeight)
                  newTabsStage.setAlwaysOnTop(false)
                }
              }
            })

            newLtp.setContent(newTabPane)
            val scene: Scene = new Scene(newLtp)
            newTabsStage.setScene(scene)
            newTabsStage.show()
          }
          else {
            tabPane.getTabs().add(tab)
            tabPane.getSelectionModel.select(tab)
          }
        }
        if (tabsStage.isShowing == false) {
          tabsStage.show()
          //println("Znowu pokazuje tabsStage")
        }

        if (!(newTabsStage.isShowing == false && notClicked)) {
          newTabsStage.show()
          //println("Znowu pokazuje newTabsStage")
        }
      }
    }
  }

  def refreshMyOrders(ladderTableView: TableView[MutableLadder], ladder: ObservableList[MutableLadder],
                    key: MarketHashMapKey, selection: String): Unit = {
    val priceProjection = PriceProjection(priceData = Set(PriceData.EX_TRADED, PriceData.EX_ALL_OFFERS))

    service.betfairServiceNG.listCurrentOrders(service.sessionToken) onComplete {
      case Success(Some(currentOrderSummaryReportContainer)) =>
        for (i <- 0 to 349) {
          if (service.myLayOrders isDefinedAt((service.odds(i), Side.LAY))){
            ladder.get(i).setMyLay(service.myLayOrders((service.odds(i), Side.LAY)))
            service.myLayOrders -= ((service.odds(i), Side.LAY))
          }
          else if (service.timersHM isDefinedAt ((service.odds(i), Side.LAY))){
            ladder.get(i).setMyLay(service.timersHM((service.odds(i), Side.LAY)).toString)
          }
          else ladder.get(i).setMyLay("")
        }
        for (i <- 0 to 349) {
          if (service.myBackOrders isDefinedAt((service.odds(i), Side.BACK))){
            ladder.get(i).setMyBack(service.myLayOrders((service.odds(i), Side.BACK)))
            service.myBackOrders -= ((service.odds(i), Side.BACK))
          }
          else if (service.timersHM isDefinedAt ((service.odds(i), Side.BACK))){
            ladder.get(i).setMyBack(service.timersHM((service.odds(i), Side.BACK)).toString)
          }
          else ladder.get(i).setMyBack("")
        }
        for (currentOrderSummary <- currentOrderSummaryReportContainer.result.currentOrders) {
          if (currentOrderSummary.selectionId.equals(service.runnersHashMap(selection))) {
            if (currentOrderSummary.status == OrderStatus.EXECUTABLE){
              if (currentOrderSummary.side == Side.LAY) {
                //TODO! TIMER
                if (service.timersHM isDefinedAt(currentOrderSummary.priceSize.price, Side.LAY)){
                  service.timersHM -= ((currentOrderSummary.priceSize.price, Side.LAY))
                }
                else {}
                if (service.myLayOrders isDefinedAt(currentOrderSummary.priceSize.price, Side.LAY)){
                  val previousStake = service.myLayOrders(currentOrderSummary.priceSize.price, Side.LAY).toInt + currentOrderSummary.priceSize.size.toInt
                  service.myLayOrders((currentOrderSummary.priceSize.price, Side.LAY)) = previousStake.toString
                }
                else{
                  service.myLayOrders += ((currentOrderSummary.priceSize.price, currentOrderSummary.side) ->
                    currentOrderSummary.priceSize.size.toInt.toString)
                }
                /*if (ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).myLayProperty.get != ""){
                  //chyba zawsze puste?
                  val previousStake = ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).myLayProperty.get.toDouble + currentOrderSummary.priceSize.size
                  ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).setMyLay(previousStake.toInt.toString)
                  if (service.myLayOrders isDefinedAt(currentOrderSummary.priceSize.price, Side.LAY)){
                    val previousStake = service.myLayOrders(currentOrderSummary.priceSize.price, Side.LAY).toInt + currentOrderSummary.priceSize.size.toInt
                    service.myLayOrders((currentOrderSummary.priceSize.price, Side.LAY)) = previousStake.toString
                  }
                  else{
                    service.myLayOrders += ((currentOrderSummary.priceSize.price, currentOrderSummary.side) ->
                      currentOrderSummary.priceSize.size.toInt.toString)
                  }
                }
                else {
                  ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).setMyLay(currentOrderSummary.priceSize.size.toInt.toString)
                  service.myLayOrders += ((currentOrderSummary.priceSize.price, currentOrderSummary.side) ->
                    currentOrderSummary.priceSize.size.toInt.toString)
                }*/
                //println(ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)))
                //ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).setMyLay(price.size.toInt.toString)
              }
              else {
                //TODO! TIMER
                if (service.timersHM isDefinedAt(currentOrderSummary.priceSize.price, Side.BACK)){
                  service.timersHM -= ((currentOrderSummary.priceSize.price, Side.BACK))
                }
                else {}
                if (service.myBackOrders isDefinedAt(currentOrderSummary.priceSize.price, Side.BACK)){
                  val previousStake = service.myBackOrders(currentOrderSummary.priceSize.price, Side.BACK).toInt + currentOrderSummary.priceSize.size.toInt
                  service.myBackOrders((currentOrderSummary.priceSize.price, Side.BACK)) = previousStake.toString
                }
                else{
                  service.myBackOrders += ((currentOrderSummary.priceSize.price, currentOrderSummary.side) ->
                    currentOrderSummary.priceSize.size.toInt.toString)
                }
                /*if (ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).myBackProperty.get != ""){
                  var previousStake = ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).myBackProperty.get.toDouble
                  previousStake += currentOrderSummary.priceSize.size
                  ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).setMyBack(previousStake.toInt.toString)
                  if (service.myBackOrders isDefinedAt(currentOrderSummary.priceSize.price, Side.BACK)){
                    val previousStake = service.myBackOrders(currentOrderSummary.priceSize.price, Side.BACK).toInt + currentOrderSummary.priceSize.size.toInt
                    service.myBackOrders((currentOrderSummary.priceSize.price, Side.BACK)) = previousStake.toString
                  }
                  else{
                    service.myBackOrders += ((currentOrderSummary.priceSize.price, currentOrderSummary.side) ->
                      currentOrderSummary.priceSize.size.toInt.toString)
                  }
                }
                else {
                  ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).setMyBack(currentOrderSummary.priceSize.size.toInt.toString)
                  service.myBackOrders += ((currentOrderSummary.priceSize.price, currentOrderSummary.side) ->
                    currentOrderSummary.priceSize.size.toInt.toString)
                }*/
                //println(ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)))
                //ladder.get(service.oddsHM(currentOrderSummary.priceSize.price)).setMyBack(price.size.toInt.toString)
              }
            }
          }
        }
      case Success(None) =>
        println("error no result returned")
      case Failure(error) =>
        println("error " + error)
    }
    /*service.myLayOrders.foreach(kv => {
      ladder.get(service.oddsHM(kv._1._1)).setMyLay(kv._2)
      service.myLayOrders -= (kv._1)
    })
    service.myBackOrders.foreach(kv => {
      ladder.get(service.oddsHM(kv._1._1)).setMyBack(kv._2)
      service.myBackOrders -= (kv._1)
    })*/
  }


  def refreshMarket(ladderTableView: TableView[MutableLadder], ladder: ObservableList[MutableLadder],
                    key: MarketHashMapKey, selection: String): Unit = {
    val priceProjection = PriceProjection(priceData = Set(PriceData.EX_TRADED, PriceData.EX_ALL_OFFERS))

    service.betfairServiceNG.listMarketBook(service.sessionToken, marketIds = Set(service.marketHashMap(key)),
      priceProjection = Some(("priceProjection", priceProjection))) onComplete {
      case Success(Some(listMarketBookContainer)) =>
        for (marketBook <- listMarketBookContainer.result) {
          delay = marketBook.betDelay
          inPlay = marketBook.inplay
          totalMatched = (marketBook.totalMatched/1000.0).toInt.toString + "k"
          for (runner <- marketBook.runners) {
            if (runner.selectionId.equals(service.runnersHashMap(selection))) {
              //println("im in")
              val rnr = runner
              //set totalMatched on this runner as volume (7) column name
              totalMatchedOnRnr = (rnr.totalMatched.get/1000.0).toInt.toString + "k"
              for (i <- 0 to 349) {
                ladder.get(i).setVol("")
              }
              for (price <- rnr.ex.get.tradedVolume) {
                ladder.get(service.oddsHM(price.price)).setVol(price.size.toInt.toString)
              }
              for (i <- 0 to 349) {
                ladder.get(i).setBack("")
              }
              for (price <- rnr.ex.get.availableToBack) {
                ladder.get(service.oddsHM(price.price)).setBack(price.size.toInt.toString)
                ladder.get(service.oddsHM(price.price)).setLay("")
              }
              for (i <- 0 to 349) {
                ladder.get(i).setLay("")
              }
              for (price <- rnr.ex.get.availableToLay) {
                ladder.get(service.oddsHM(price.price)).setLay(price.size.toInt.toString)
                ladder.get(service.oddsHM(price.price)).setBack("")
              }
              scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]) {
                val lpt = service.oddsHM(rnr.lastPriceTraded.get)
              }
              Platform.runLater(new Runnable() {
                override def run(): Unit = {
                  ladderTableView.getColumns.get(7).setText(totalMatchedOnRnr)
                }
              })
            } else {
              //println("to nie to")
            }
          }
        }
      case Success(None) =>
        println("error no result returned")
      case Failure(error) =>
        println("error " + error)
    }

    //var layTotal
  }

  def openLadder(item: TreeItem[Object], key: MarketHashMapKey, name: String) {
    scala.util.control.Exception.ignoring(classOf[java.lang.RuntimeException]) {
      scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]) {
        val tab: Tab = new Tab()
        openMarket(item, key, name, tab)
      }
    }
  }

  //TODO! LIVE VERSION!
  //def placeholder, "" -> placeBet; "fiz" -> cancelBet
  def placeBet(marketId: String, selectionId: Int, size: Double, price: Double, side: Side.Side): Unit = {
    val placeInstructions = Set(PlaceInstruction(orderType = OrderType.LIMIT, selectionId,
      handicap = Some(0.0), side,
      limitOrder = Some(LimitOrder(size, price, persistenceType = PersistenceType.PERSIST))))
    //TODO! TIMER!
    if (service.timersHM isDefinedAt (price, side)){
      if (service.timersHM(price, side).under3){
        if (service.timersHM(price, side).timCounter == 1){
          service.timersHM(price, side).startSndTim()
        }
        else service.timersHM(price, side).startThrdTim()
      }
      else {}
    }
    else{
      val tim = new BetTimer(delay)
      service.timersHM += ((price, side) -> tim)
      tim.startFrstTim()
    }

    service.betfairServiceNG.placeOrders(service.sessionToken, marketId, instructions = placeInstructions
    ) onComplete {
      case Success(Some(placeExecutionReportContainer)) =>
        println("Place Execution Report is: " + placeExecutionReportContainer)
      case Success(None) =>
        println("error no result returned")
      case Failure(error) =>
        println("error " + error)
    }
  }

  //replaceOrders is bet price change
  def replaceBet(marketId: String, betId: String, newPrice: Double): Unit = {
    val replaceInstructions = Set(ReplaceInstruction(betId: String, newPrice: Double))
    service.betfairServiceNG.replaceOrders(service.sessionToken, marketId, instructions = replaceInstructions
    ) onComplete {
      case Success(Some(replaceExecutionReportContainer)) =>
        println("Replace Execution Report is: " + replaceExecutionReportContainer)
      case Success(None) =>
        println("error no result returned")
      case Failure(error) =>
        println("error " + error)
    }
  }

  //updateOrders is now a trivial bet persistence change
  def updateBet(marketId: String, betId: String, newStake: Double): Unit = {}

  //cancelOrders is used to reduce the bet size (portion cancel)
  def cancelBet(marketId: String, betId: String, sizeReduction: Option[Double] = None): Unit = {
    val cancelInstructions = Set(CancelInstruction(betId, sizeReduction))
    service.betfairServiceNG.cancelOrders(service.sessionToken, marketId, instructions = cancelInstructions) onComplete {
      case Success(Some(cancelExecutionReportContainer)) =>
        println("Cancel Execution Report is: " + cancelExecutionReportContainer)
      case Success(None) =>
        println("error no result returned")
      case Failure(error) =>
        println("error " + error)
    }
  }

  //TODO! tu i dopisać na kolumny, zrobić dokowanie i always on top, zapytać o format nieprzyjętych
  //def hedge(marketId: String, selectionId: Int, size: Double, price: Double, side: Side.Side): Unit ={}

  //def hedgePartial(marketId: String, selectionId: Int, size: Double, price: Double, side: Side.Side): Unit ={}


  //wywalic do navigatora? i printowac do scrollpane
  def listCurrentBets(): Unit = {
    println("TESTO: listCurrentBets")
  }
}

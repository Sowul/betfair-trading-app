package app.controllers

import java.awt.MouseInfo
import java.io._

import app._

import scala.concurrent.duration._
import collection.mutable.{ArrayBuffer, HashMap}
import scala.util.control.Breaks._
import javafx.beans.value.{ChangeListener, ObservableValue}
import javafx.fxml.{FXML, FXMLLoader, Initializable}
import javafx.scene.control._
import javafx.scene.image.{Image, ImageView}
import javafx.collections.ListChangeListener
import javafx.collections.ListChangeListener.Change

import com.betfair.domain._

import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Random, Success, Try}
import scala.language.postfixOps
import javafx.application.Platform

import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.ISODateTimeFormat
import com.github.nscala_time.time.OrderingImplicits.DateTimeOrdering
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file._
import java.util.{ArrayList, Optional, ResourceBundle}
import javafx.beans.value.ObservableValue
import javafx.collections.ObservableList
import javafx.event.{ActionEvent, Event, EventHandler}
import javafx.scene.control.Alert.AlertType
import javafx.scene.control.TabPane.TabClosingPolicy
import javafx.scene.{Node, Parent, Scene}
import javafx.scene.control.{TableColumn, TableView}
import javafx.scene.input._
import javafx.scene.layout.{GridPane, VBox}
import javafx.stage.{Stage, WindowEvent}

import akka.actor.Props
import com.jfoenix.controls.{JFXCheckBox, JFXComboBox, JFXTextField}
import com.typesafe.config.ConfigFactory

import scala.io.Source

class NavigatorController extends Initializable {

  val bta: TitledPane = new TitledPane()

  def dockNavigator(): Unit ={
    import com.sun.javafx.stage.StageHelper
    val stage = StageHelper.getStages().get(0)
    if(bta.isExpanded){
      bta.setExpanded(false)
      //stage.setMinHeight(56)
      //stage.setHeight(56)
      stage.setMinHeight(0.073*service.screenHeight)
      stage.setHeight(0.073*service.screenHeight)
      stage.setAlwaysOnTop(true)
    }
    else{
      bta.setExpanded(true)
      //stage.setHeight(service.screenHeight-50)
      stage.setHeight(0.934*service.screenHeight)
      stage.setAlwaysOnTop(false)
    }
  }

  @FXML var cancelCheckBox: JFXCheckBox = _
  @FXML var closeCheckBox: JFXCheckBox = _
  @FXML var minTextField: TextField = _

  @FXML var eventChoiceBox: ChoiceBox[String] = new ChoiceBox[String]

  @FXML var splitPane: SplitPane = _

  @FXML var tp: TitledPane = _
  @FXML var myMarkets: TitledPane = _

  @FXML var eventTreeView: TreeView[Object] = _

  @FXML var table: TableView[MutableMyMarket] = _
  type MyMarketTC[T] = TableColumn[MutableMyMarket, T]
  @FXML var marketCol: MyMarketTC[String] = _
  @FXML var alarmCol: MyMarketTC[String] = _

  val ladder = new LadderInterface()

  import app.JfxUtils._

  /**
    * provide a table column and a generator function for the value to put into
    * the column.
    *
    * @tparam T the type which is contained in the property
    * @return
    */
  def initTableViewColumn[T]: (MyMarketTC[T], (MutableMyMarket) => Any) => Unit = {
    initTableViewColumnCellValueFactory[MutableMyMarket, T]
  }

  override def initialize(location: URL, resources: ResourceBundle): Unit = {

    initTableViewColumn[String](marketCol, _.marketNameProperty)
    initTableViewColumn[String](alarmCol, _.alarmOddsProperty)

    import javafx.scene.control.cell.TextFieldTableCell
    alarmCol.setCellFactory(TextFieldTableCell.forTableColumn())
    alarmCol.setOnEditCommit(new EventHandler[TableColumn.CellEditEvent[MutableMyMarket, String]]() {
      def handle(e: TableColumn.CellEditEvent[MutableMyMarket, String]) {
        //println("deleting old row")
        val mrkt: String = table.getSelectionModel.getSelectedItem.marketNameProperty.getValueSafe
        val alrm: String = table.getSelectionModel.getSelectedItem.alarmOddsProperty.getValueSafe
        def toRemove(x: MyMarket): Boolean = !(x.marketName == mrkt && x.alarmOdds == alrm)
        DataSource.data = DataSource.data.filter(toRemove)
        //println("old & new before change")
        //println(e.getOldValue)
        //println(e.getNewValue)
        val market = service.favouriteHashMap((marketCol.getCellData(e.getTablePosition.getRow), e.getOldValue))
        //println("got market")
        service.favouriteHashMap((marketCol.getCellData(e.getTablePosition.getRow), alarmCol.getCellData(e.getTablePosition.getRow))).changeAlarmOdds(e.getNewValue.toDouble)
        //println("market alarm odds changed")
        service.favouriteHashMap += ((marketCol.getCellData(e.getTablePosition.getRow), e.getNewValue) -> service.favouriteHashMap((marketCol.getCellData(e.getTablePosition.getRow), alarmCol.getCellData(e.getTablePosition.getRow))))
        //println("new key created")
        //println(e.getOldValue)
        //println(e.getNewValue)
        //println("new alarm odds set")
        //service.favouriteHashMap -= ((marketCol.getCellData(e.getTablePosition.getRow), e.getOldValue))
        //musi zostać, bo inaczej nie zapisze przy zamknięciu

        e.getRowValue.setAlarmOdds(e.getNewValue)
        //println("old key deleted")

        DataSource.data = MyMarket(mrkt, e.getNewValue) :: DataSource.data
        val mMarkets = mkObservableList(DataSource.data.map(MutableMyMarket(_)))
        table.setItems(mMarkets)
      }
    })

    cancelCheckBox.setOnMouseClicked(new EventHandler[MouseEvent](){
      override def handle(e: MouseEvent){
        service.cancelCheckBox = cancelCheckBox.isSelected
      }
    })
    closeCheckBox.setOnMouseClicked(new EventHandler[MouseEvent](){
      override def handle(e: MouseEvent){
        service.closeCheckBox = closeCheckBox.isSelected
      }
    })
    minTextField.textProperty().addListener(new ChangeListener[String]{
      def changed(observable: ObservableValue[_ <: String], oldValue: String, newValue: String): Unit = {
        service.minTextField = minTextField.getText
      }
    })

    loadMyMarkets()
    loadCancelConf()

  }

  //val ldr = new LadderController()
  //val alrm = akka.actor.ActorSystem("alrm")
  //alrm.scheduler.schedule(0 seconds, 15 seconds)({
  //  ldr.checkAlarm()
  //})

  var ipChecked: Boolean = false
  def checkIp(): Unit = {
    //TODO! spr co jest nie tak
    val whatismyip: URL = new URL("http://checkip.amazonaws.com")
    var in: BufferedReader = new BufferedReader(new InputStreamReader(whatismyip.openStream()))
    var ip: String = in.readLine() //you get the IP as a String
    if (ipChecked == false){
      ipChecked = true
      service.ip = ip
    }
    else{
      if(ip != service.ip) {
        val alert: Alert = new Alert(AlertType.ERROR)
        alert.setTitle("Error")
        alert.setHeaderText("IP changed.")
        alert.showAndWait()
      }
    }
    //println(ip)
  }
  val system = akka.actor.ActorSystem("system")
  system.scheduler.schedule(0 seconds, 5 seconds)({
    checkIp()
  })
  //cancelAfter()
  system.scheduler.schedule(0 seconds, 24 hours)({
    var idleTime = 0L
    var start = System.currentTimeMillis()
    var currLocation = MouseInfo.getPointerInfo().getLocation()
    while (true) {
      //println(service.minTextField)
      Thread.sleep(1000)
      val newLocation = MouseInfo.getPointerInfo().getLocation()
      if (newLocation.equals(currLocation)) {
        scala.util.control.Exception.ignoring(classOf[java.lang.NumberFormatException]){
          //not moved
          idleTime = System.currentTimeMillis() - start
          //println(idleTime)
          //min to milliseconds
          if (idleTime >= service.minTextField.toLong*10000){
            if (service.cancelCheckBox){
              scala.util.control.Exception.ignoring(classOf[java.lang.IllegalStateException]){
                autocloseBets()
                Thread.sleep(3000)
              }
            }
            else{}
          }
        }
      } else {
        //println("Idle time was: $idleTime ms", idleTime)
        idleTime = 0L
        start = System.currentTimeMillis()
      }
      currLocation = newLocation
    }
  })

  var eventId: Int = _

  //TODO! tutaj warto byłoby zoptymalizować pobieranie listy rynków
  def getEvent() {
    eventChoiceBox.getSelectionModel().selectedItemProperty()
      .addListener(new ChangeListener[String] {
        def changed(observable: ObservableValue[_ <: String], oldValue: String, newValue: String): Unit = {
          //printuje tyle razy ile kliknieto, ale przy prownaniu siedzi jedna wartosc

          eventId = getEventId(newValue)

          val rootItem: TreeItem[Object] = new TreeItem[Object](newValue)
          rootItem.setExpanded(true)

          val listCompetitionsMarketFilter = new MarketFilter(eventTypeIds = Set(eventId))
          service.betfairServiceNG.listCompetitions(service.sessionToken, listCompetitionsMarketFilter) onComplete {
            case Success(Some(competitionResultContainer)) =>
              //val competitionTemp = competitionResultContainer.result.sortBy(_.competition.name)
              //val cId = competitionTemp(0).competition.id.toInt
              breakable {
                for (competitionResult <- competitionResultContainer.result.sortBy(_.competition.name)) {
                  if (eventId != getEventId(newValue)) break()
                  val item: TreeItem[Object] = new TreeItem[Object](competitionResult.competition.name)
                  val cId = competitionResult.competition.id.toInt
                  service.betfairServiceNG.listEvents(service.sessionToken, new MarketFilter(competitionIds = Set(cId))) onComplete {
                    case Success(Some(eventResultContainer)) =>
                      //print(eventResultContainer.result)
                      //eventResultContainer.result.sortBy(_.event.name) event.id
                      breakable {
                        for (eventResult <- eventResultContainer.result.sortBy(_.event.openDate)) {
                          if (eventId != getEventId(newValue)) break()
                          val testo: TreeItem[Object] = new TreeItem[Object](eventResult.event.name)
                          eventResult.event.name match {
                            case "Set 01" | "Set 02" | "Set 03" => print("")
                            case _ => item.getChildren.add(testo)
                          }

                          val listMarketCatalogueMarketFilter = new MarketFilter(eventIds = Set(eventResult.event.id.toInt))
                          service.betfairServiceNG.listMarketCatalogue(service.sessionToken, listMarketCatalogueMarketFilter,
                            List(MarketProjection.MARKET_START_TIME,
                              MarketProjection.RUNNER_DESCRIPTION,
                              MarketProjection.EVENT_TYPE,
                              MarketProjection.EVENT,
                              MarketProjection.COMPETITION), MarketSort.FIRST_TO_START, 200
                          ) onComplete {
                            case Success(Some(listMarketCatalogueContainer)) =>
                              /////WYWALIĆ 116,117,120,121,126
                              val fmtHour: DateTimeFormatter = ISODateTimeFormat.hourMinute()
                              val fmtDate: DateTimeFormatter = DateTimeFormat.forPattern("dd/MM")
                              breakable {
                                for (marketCatalogue <- listMarketCatalogueContainer.result.sortBy(_.marketId)) {
                                  if (eventId != getEventId(newValue)) break()
                                  //println("Market Catalogue is: " + marketCatalogue)
                                  val hourStr: String = fmtHour.print(marketCatalogue.marketStartTime.get)
                                  val dateStr: String = fmtDate.print(marketCatalogue.marketStartTime.get)
                                  //println(marketCatalogue.marketStartTime.get)
                                  val testo2: TreeItem[Object] = new TreeItem[Object](marketCatalogue)
                                  val key = new MarketHashMapKey(dateStr + " " + hourStr + " " + eventResult.event.name,
                                    marketCatalogue.marketName)
                                  service.marketHashMap += (key -> marketCatalogue.marketId)
                                  service.teamsHashMap += (key -> marketCatalogue.runners.get)
                                  //item.getParent.getValue.toString + " / " + item.getValue.toString jako klucz?
                                  //println(marketCatalogue.runners.get)
                                  testo.getChildren.add(testo2)
                                  testo.setValue(dateStr + " " + hourStr + " " + eventResult.event.name)
                                  val priceProjection = PriceProjection(priceData = Set(PriceData.EX_BEST_OFFERS))
                                  service.betfairServiceNG.listMarketBook(service.sessionToken, marketIds = Set(marketCatalogue.marketId),
                                    priceProjection = Some(("priceProjection", priceProjection))) onComplete {
                                    case Success(Some(listMarketBookContainer)) =>
                                      for (marketBook <- listMarketBookContainer.result) {
                                        Platform.runLater(new Runnable() {
                                          override def run() {
                                            if (marketBook.inplay == true){
                                              val img: Image = new Image("inplay.png")
                                              val imgV: ImageView = new ImageView()
                                              imgV.setImage(img)
                                              imgV.setFitWidth(10)
                                              imgV.setPreserveRatio(true)
                                              testo.setGraphic(imgV)
                                            }
                                            else {
                                              val img: Image = new Image("preplay.png")
                                              val imgV: ImageView = new ImageView()
                                              imgV.setImage(img)
                                              imgV.setFitWidth(10)
                                              imgV.setPreserveRatio(true)
                                              testo.setGraphic(imgV)
                                            }
                                          }
                                        })
                                      }
                                    case Success(None) =>
                                      println("error no result returned")
                                    case Failure(error) =>
                                      println("error " + error)
                                  }
                                }
                              }

                            case Success(None) =>
                              println("error no result returned")
                            case Failure(error) =>
                              println("error " + error)
                          }
                          ///WYJEBAĆ
                        }
                      }
                    case _ => println("D")
                  }
                  rootItem.getChildren().add(item)
                }

                Platform.runLater(new Runnable() {
                  override def run() {
                    eventTreeView.setRoot(rootItem)
                    eventTreeView.setShowRoot(false)
                  }
                })
              }

            case Success(None) =>
              println("error no result returned")
            case Failure(error) =>
              println("error " + error)
          }
        }
      })
  }

  def openLadder() {
    scala.util.control.Exception.ignoring(classOf[java.lang.RuntimeException]) {
      scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]) {
        val item: TreeItem[Object] = eventTreeView.getSelectionModel().getSelectedItem()
        if (item.isLeaf){
          val key = new MarketHashMapKey(item.getParent().getValue.toString, item.getValue.toString)
          //println(item.getParent().getValue.toString)
          //println(item.getValue.toString)
          val name: String = item.getParent.getValue.toString + " / " + item.getValue.toString
          //println("Przed sleepem: ", name)
          val tab: Tab = new Tab()
          ladder.openLadder(item, key, name)
          //ladder.openMarket(item, key, name, tab)
        }
      }
    }
  }

  def openMyMarket(): Unit ={
    scala.util.control.Exception.ignoring(classOf[java.lang.RuntimeException]) {
      scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]) {
        val selectedCells: ObservableList[TablePosition[_,_]] = table.getSelectionModel().getSelectedCells()
        val tablePosition = selectedCells.get(0)
        val item = new TreeItem[Object](tablePosition.getTableColumn().getCellData(tablePosition.getRow()).toString)
        //println(item)
        val name = service.noOddsHashMap(tablePosition.getTableColumn().getCellData(tablePosition.getRow()).toString)
        //println(name)
        val key = service.myMarketsHashMap(name)
        //println(key)
        val tab: Tab = new Tab()
        tab.setStyle("-fx-background-color: lightblue")
        ladder.openMarket(item, key, name, tab)
        //println("opened")
      }
    }
  }

  def removeMarket() {
    scala.util.control.Exception.ignoring(classOf[java.lang.reflect.InvocationTargetException]) {
      scala.util.control.Exception.ignoring(classOf[java.lang.NullPointerException]){
        val mrkt: String = table.getSelectionModel.getSelectedItem.marketNameProperty.getValueSafe
        val alrm: String = table.getSelectionModel.getSelectedItem.alarmOddsProperty.getValueSafe
        //println(mrkt)
        def toRemove(x: MyMarket): Boolean = !(x.marketName == mrkt && x.alarmOdds == alrm)

        service.favouriteHashMap((mrkt, alrm)).alarmDisabled = true
        service.alarm = false

        service.favouriteHashMap -= ((mrkt, alrm))
        service.noOddsHashMap -= mrkt

        DataSource.data = DataSource.data.filter(toRemove)
        val mMarkets = mkObservableList(DataSource.data.map(MutableMyMarket(_)))
        table.setItems(mMarkets)
      }
    }
  }

  def loadMyMarkets(): Unit ={
    val filename = "my_markets.txt"
    for (line <- Source.fromFile(filename).getLines()) {
      //println(line.isEmpty)
      if (line.isEmpty == false){
        val params = line.split(",")
        service.betfairServiceNG.listMarketBook(service.sessionToken, marketIds = Set(params(2))) onComplete {
          case Success(Some(listMarketBookContainer)) =>
            //println(listMarketBookContainer.result(0).status)
            if(listMarketBookContainer.result(0).status == "CLOSED"){
              //usunąć linię z pliku
            }
            else{
              service.noOddsHashMap += (params(0) -> params(1))
              service.myMarketsHashMap += (params(1) -> new MarketHashMapKey(params(5), params(6)))
              service.marketHashMap += (service.myMarketsHashMap(params(1)) -> params(2))
              getRunners(service.marketHashMap(service.myMarketsHashMap(params(1))), service.myMarketsHashMap(params(1)))
              DataSource.data = MyMarket(params(0), params(4)) :: DataSource.data
              val mMarkets = mkObservableList(DataSource.data.map(MutableMyMarket(_)))
              table.setItems(mMarkets)
              if(params(4) != ""){
                val aMarket = new AlarmMarket(params(0), params(1), params(2), params(3).toLong, params(4).toDouble, params(7))
                service.favouriteHashMap += ((params(0), params(4)) -> aMarket)
              }
              else {
                val aMarket = new AlarmMarket(params(0), params(1), params(2), params(3).toLong, params(4).toDouble, params(7))
                service.favouriteHashMap += ((params(0), params(4)) -> aMarket)
              }
            }
            //println(listMarketBookContainer.result(0).inplay)
            //println("your markets loaded")
          case Success(None) =>
            println("error no result returned")
          case Failure(error) =>
            println("error " + error)
        }
      }
    }
    println("your markets loaded")
  }

  def addToMyMarkets(): Unit = {
    scala.util.control.Exception.ignoring(classOf[java.lang.RuntimeException]) {
      scala.util.control.Exception.ignoring(classOf[java.util.NoSuchElementException]) {
        val item: TreeItem[Object] = eventTreeView.getSelectionModel().getSelectedItem()
        if (item.isLeaf) {
          import java.util.ArrayList
          val name: String = item.getParent.getValue.toString + " / " + item.getValue.toString
          val key = new MarketHashMapKey(item.getParent().getValue.toString, item.getValue.toString)
          //println(name.slice(12, name.length))
          service.myMarketsHashMap += (name -> key)
          ////TESTO
          ////TESTOEND
          val runners = service.teamsHashMap(key)
          //tu chyba bez regexa się nie obejdzie, choć jakoś te nazwy rynków muszą przychodzić
          //////
          var choices: ArrayList[String] = new ArrayList[String]()
          //////
          //dodać selekcje z rynku
          //println("STARTO")
          //println(runners)
          for (runner <- runners) {
            service.runnersHashMap += (runner.runnerName -> runner.selectionId)
            //println(runner.selectionId)
            choices.add(runner.runnerName)
          }

          //to na funkcję?
          var selectionDialog: ChoiceDialog[String] = new ChoiceDialog[String]("", choices)
          selectionDialog.setTitle("")
          selectionDialog.setHeaderText("")
          selectionDialog.setContentText("Choose selection:")

          // Traditional way to get the response value.
          var selection: Optional[String] = selectionDialog.showAndWait()
          if (selection.isPresent()) {
            //System.out.println("Your choice: " + selection.get())
            ///
            var dialog: TextInputDialog = new TextInputDialog("")
            dialog.setTitle("")
            dialog.setHeaderText("")
            dialog.setContentText("Set alarm odds:")

            // Traditional way to get the response value.
            var odds: Optional[String] = dialog.showAndWait()
            //if (odds.isPresent() && odds.get.toDouble >= 1.01 && odds.get.toDouble <= 1000)
            if (odds.isPresent()) {
              service.noOddsHashMap += (selection.get() -> name)
              //System.out.println("Alarm odds: " + odds.get())
              DataSource.data = MyMarket(selection.get(), odds.get) :: DataSource.data
              //DataSource.data = MyMarket(name.slice(12, name.length), odds.get) :: DataSource.data
              val mMarkets = mkObservableList(DataSource.data.map(MutableMyMarket(_)))
              table.setItems(mMarkets)

              ////
              var alert: Alert = new Alert(AlertType.CONFIRMATION)
              alert.setTitle("Alarm odds setup")
              //alert.setHeaderText("Look, a Confirmation Dialog with Custom Actions")
              alert.setContentText("Choose your option.")

              val buttonTypeOne: ButtonType = new ButtonType("under")
              val buttonTypeTwo: ButtonType  = new ButtonType("over")

              alert.getButtonTypes().setAll(buttonTypeOne, buttonTypeTwo)

              val result: Optional[ButtonType] = alert.showAndWait()
              var uo = ""
              if (result.get() == buttonTypeOne){
                uo = "under"
              }
              else {
                uo = "over"
              }
              ////

              if(odds.get != ""){
                val aMarket = new AlarmMarket(selection.get(),name,service.marketHashMap(service.myMarketsHashMap(name)), service.runnersHashMap(selection.get), odds.get.toDouble, uo)
                service.favouriteHashMap += ((selection.get(), odds.get) -> aMarket)
              }
              else{
                val aMarket = new AlarmMarket(selection.get(),name,service.marketHashMap(service.myMarketsHashMap(name)), service.runnersHashMap(selection.get), 0, uo)
                service.favouriteHashMap += ((selection.get(), 0.toString) -> aMarket)
              }
              //println("dodalem do pliku 1")
              //TODO! dokończyć, sprawdzanie co 15 sekund, kontrolka, tooltip z nazwą rynku
              val fw = new FileWriter("my_markets.txt", true)
              if(odds.get != ""){
                val content = selection.get()+","+name+","+service.marketHashMap(service.myMarketsHashMap(name))+","+service.runnersHashMap(selection.get).toString+","+odds.get+","+item.getParent().getValue.toString+","+item.getValue.toString+","+uo+"\n"
                fw.write(content)
                fw.close()
              }
              else {
                val content = selection.get()+","+name+","+service.marketHashMap(service.myMarketsHashMap(name))+","+service.runnersHashMap(selection.get).toString+","+0.toString+","+item.getParent().getValue.toString+","+item.getValue.toString+","+uo+"\n"
                fw.write(content)
                fw.close()
              }

              //println("dodalem do pliku 2")
            }

            ////TESTO
            ///
            //
            // openMarket?
            val tab: Tab = new Tab()
            tab.setStyle("-fx-background-color: lightblue")
            //tab.setText(name)
            ladder.openMarket(item, key, name, tab)
          }
        }
      }
    }
  }

  def getEventId(x: String): Int = x match {
    case "American Football" => 6423
    case "Athletics" => 3988
    case "Australian Rules" => 61420
    case "Baseball" => 7511
    case "Basketball" => 7522
    case "Bowls" => 998918
    case "Boxing" => 6
    case "Chess" => 136332
    case "Cricket" => 4
    case "Cycling" => 11
    case "Darts" => 3503
    case "E-Sports" => 27454571
    case "Financial Bets" => 6231
    case "Football" => 1
    case "Gaelic Games" => 2152880
    case "Golf" => 3
    case "Greyhound Racing" => 4339
    case "Handball" => 468328
    case "Horse Racing" => 7
    case "Ice Hockey" => 7524
    case "Mixed Martial Arts" => 26420387
    case "Motor Sport" => 8
    case "Poker" => 315220
    case "Politics" => 2378961
    case "Pool" => 72382
    case "Rugby League" => 1477
    case "Rugby Union" => 5
    case "Snooker" => 6422
    case "Special Bets" => 10
    case "Tennis" => 2
    case _ => 0
  }

  def getRunners(mId: String, key: MarketHashMapKey){
    val listMarketCatalogueMarketFilter = new MarketFilter(marketIds = Set(mId))
    service.betfairServiceNG.listMarketCatalogue(service.sessionToken, listMarketCatalogueMarketFilter,
      List(MarketProjection.MARKET_START_TIME,
        MarketProjection.RUNNER_DESCRIPTION,
        MarketProjection.EVENT_TYPE,
        MarketProjection.EVENT,
        MarketProjection.COMPETITION), MarketSort.FIRST_TO_START, 200
    ) onComplete {
      case Success(Some(listMarketCatalogueContainer)) =>
        for (marketCatalogue <- listMarketCatalogueContainer.result.sortBy(_.marketId)) {
          service.teamsHashMap += (key -> marketCatalogue.runners.get)
        }
      case Success(None) =>
        println("error no result returned")
      case Failure(error) =>
        println("error " + error)
    }
  }

  def loadCancelConf(): Unit ={
    val conf = ConfigFactory.load("cancel")
    //val conf = ConfigFactory.parseFile(new File("cancel.conf"))
    val cancelAfter = conf.getString("cancelAfter")
    val autoclose = conf.getString("autoclose")

    if(cancelAfter.split(",")(0) == "ok"){
      cancelCheckBox.setSelected(true)
      service.cancelCheckBox = true
      minTextField.setText(cancelAfter.split(",")(1))
      service.minTextField = cancelAfter.split(",")(1)
    }
    else{
      cancelCheckBox.setSelected(false)
    }

    if(autoclose == "ok"){
      closeCheckBox.setSelected(true)
      service.closeCheckBox = true
    }
    else{
      closeCheckBox.setSelected(false)
    }
    println("your cancel conf loaded")
  }

  def cancelAfter(): Unit = {
    //println("Bets will be closed after $minTextField.getText minutes of inactivity.")
    var idleTime = 0L
    var start = System.currentTimeMillis()
    var currLocation = MouseInfo.getPointerInfo().getLocation()
    breakable {
      while (true) {
        //println(service.minTextField)
        Thread.sleep(1000)
        val newLocation = MouseInfo.getPointerInfo().getLocation()
        if (newLocation.equals(currLocation)) {
          //not moved
          idleTime = System.currentTimeMillis() - start
          //min to milliseconds
          if (idleTime >= service.minTextField.toLong*60000){
            println("KURWA")
          }
        } else {
          println("Idle time was: %s ms", idleTime)
          idleTime = 0L
          start = System.currentTimeMillis()
          break
        }
        currLocation = newLocation
      }
    }
  }

  def autocloseBets(): Unit = {
    println("Bets will be autoclosed on exit.")
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
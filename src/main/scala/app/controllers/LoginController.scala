package app.controllers

import javafx.fxml.{FXML, FXMLLoader}
import javafx.scene.layout.AnchorPane
import javafx.scene.{Parent, Scene}
import javafx.stage.Stage
import javafx.scene.control.{Alert, TextField, TextArea}
import javafx.scene.control.Alert.AlertType
import javafx.scene.input.{KeyCode, KeyEvent}


import com.jfoenix.controls.{JFXTextArea, JFXPasswordField, JFXButton}
import com.jfoenix.skins.JFXTextAreaSkin
import akka.actor.ActorSystem
import com.betfair.Configuration
import com.betfair.service._
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent._
import scala.language.postfixOps

class LoginController {

  @FXML var loginPane: AnchorPane = _

  @FXML var user: JFXTextArea = _
  @FXML var pass: JFXPasswordField = _
  @FXML var log: JFXButton = _

  def login(): Unit = {
    //println("ok")
    implicit val system = ActorSystem()
    import system.dispatcher
    val conf = ConfigFactory.load()
    val appKey = conf.getString("betfairService.appKey")
    //println(appKey)
    val username = user.getText()
    val password = pass.getText()
    val apiUrl = conf.getString("betfairService.apiUrl")
    val isoUrl = conf.getString("betfairService.isoUrl")

    val config = new Configuration(appKey, username, password, apiUrl, isoUrl)
    val command = new BetfairServiceNGCommand(config)

    service.betfairServiceNG = new BetfairServiceNG(config, command)

    // log in to obtain a session id
    val sessionTokenFuture = service.betfairServiceNG.login map {
      case Some(loginResponse) => {
        loginResponse.token
      }
      case _ => {
        throw new BetfairServiceNGException("no session token")
      }
    }

    service.sessionToken = Await.result(sessionTokenFuture, 60 seconds)
    println(service.sessionToken)
    if (service.sessionToken.length != 0) changeStage() else {
      val alert: Alert = new Alert(AlertType.ERROR)
      alert.setTitle("Error")
      alert.setHeaderText("Wrong username or password")
      alert.showAndWait()
      //println("wtf")
    }
  }

  def changeStage(): Unit = {
    loginPane.getScene().getWindow().hide()
    val root: Parent = FXMLLoader.load(getClass().getResource("/fxml/Navigator.fxml"))
    val scene: Scene = new Scene(root)
    val stage: Stage = new Stage

    // determine the screen resolution
    //val screenSize: Dimension = Toolkit.getDefaultToolkit().getScreenSize()
    //val screenHeight = screenSize.height
    //val screenWidth = screenSize.width
    // start in upper left corner
    stage.setX(0)
    stage.setY(0)
    stage.setWidth(0.2*service.screenWidth)
    stage.setMinWidth(0.2*service.screenWidth)
    //stage.setHeight(service.screenHeight-50)
    stage.setHeight(0.934*service.screenHeight)
    stage.setScene(scene)
    stage.setTitle("Betfair Trading App")
    //stage.setAlwaysOnTop(true)
    import javafx.stage.StageStyle
    //stage.initStyle(StageStyle.DECORATED)
    stage.show()
  }

  def handle(event: KeyEvent) {
      if (event.getCode() == KeyCode.TAB) {
        var node = event.getSource
        if (node.isInstanceOf[TextArea]) {
          if (event.isShiftDown()) {
            user.getSkin().asInstanceOf[JFXTextAreaSkin].getBehavior().traversePrevious()
            }
          else {
            user.getSkin().asInstanceOf[JFXTextAreaSkin].getBehavior().traverseNext()
          }
        }
        else if (node.isInstanceOf[TextField]) {
          if (event.isShiftDown()) {
            user.getSkin().asInstanceOf[JFXTextAreaSkin].getBehavior().traversePrevious()
          }
          else {
            user.getSkin().asInstanceOf[JFXTextAreaSkin].getBehavior().traverseNext()
          }
        }
        event.consume()
      }
  }

}

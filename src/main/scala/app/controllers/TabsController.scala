package app.controllers

import com.jfoenix.controls.JFXTabPane
import javafx.scene.control.{TabPane, TitledPane}
import javafx.fxml.FXML
import javafx.scene.control.TabPane.TabClosingPolicy

class TabsController {

  @FXML var tabPane: TabPane = _

  def closeMarket() {
    println("market closed")
  }
}

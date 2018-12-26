package app

import app.controllers.service

object DataSource {

  var data = List[MyMarket]()
  var ladder = List[Ladder]()
  for (i <- 0 to 349) {
    DataSource.ladder = Ladder(odds = service.odds(i)) :: DataSource.ladder
  }

}

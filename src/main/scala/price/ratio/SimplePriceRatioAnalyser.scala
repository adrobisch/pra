package price.ratio

import java.io.InputStream

import scala.io.Source

trait SimplePriceRatioAnalyser {

  val InputLinePattern = "([0-9]+)\\s+(.*)".r

  def analyse(inputStream: InputStream, windowLength: Int = 60) = {
      var currentWindow: Seq[PriceRatioMeasurement] = List()
    
      Source
        .fromInputStream(inputStream)
        .getLines()
        .map {
          case InputLinePattern(timestamp, ratio) => PriceRatioMeasurement(timestamp.toLong, BigDecimal(ratio.toDouble))
          case _ => throw new IllegalArgumentException("invalid line format")   
        }
        .map { currentMeasurement =>
          currentWindow = (currentWindow :+ currentMeasurement).dropWhile(_.timestamp < currentMeasurement.timestamp - windowLength)

          analyseCurrentWindow(currentWindow, currentMeasurement)
        }
  }

  def analyseCurrentWindow(currentWindow: Seq[PriceRatioMeasurement], measurement: PriceRatioMeasurement): PriceRatioAnalysis = {
    currentWindow.foldLeft(
      PriceRatioAnalysis(measurement.timestamp,
        measurement.ratio,
        0,
        0,
        measurement.ratio,
        measurement.ratio))(
      (window, measurement) =>
        window.copy(
          windowEnd = measurement.timestamp,
          ratioCount = window.ratioCount + 1,
          ratioSum = window.ratioSum + measurement.ratio,
          maxRatio =
            if (window.maxRatio > measurement.ratio) window.maxRatio
            else measurement.ratio,
          minRatio =
            if (window.minRatio < measurement.ratio) window.minRatio
            else measurement.ratio))
  }
}


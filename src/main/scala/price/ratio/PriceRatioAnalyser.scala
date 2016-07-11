package price.ratio

import java.nio.file.{Path, Paths}
import java.text.DecimalFormat

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Framing.delimiter
import akka.stream.scaladsl._
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

case class PriceRatioMeasurement(timestamp: Long, ratio: BigDecimal)

case class PriceRatioAnalysis(windowEnd: Long,
                              currentRatio: BigDecimal,
                              ratioCount: Int,
                              ratioSum: BigDecimal,
                              minRatio: BigDecimal,
                              maxRatio: BigDecimal) {

  def format =
    s"$windowEnd ${decimalFormat(currentRatio)} $ratioCount ${decimalFormat(
        ratioSum)} ${decimalFormat(minRatio)} ${decimalFormat(maxRatio)}"

  def decimalFormat(number: BigDecimal) =
    new DecimalFormat("#0.#####").format(number).padTo(7, " ").mkString
}

trait PriceRatioAnalyser {
  val InputLinePattern = "([0-9]+)\\s+(.*)".r

  def analyse(file: Path): Source[PriceRatioAnalysis, _] =
    FileIO
      .fromPath(file)
      .via(delimiter(ByteString(System.lineSeparator),
                     maximumFrameLength = 512,
                     allowTruncation = false))
      .map(_.utf8String)
      .map {
        case InputLinePattern(timestamp, ratio) =>
          PriceRatioMeasurement(timestamp.toLong, BigDecimal(ratio.toDouble))
        case _ => throw new IllegalArgumentException("wrong line format")
      }
      .via(PriceRatioAnalysisStage)
}

object PriceRatioAnalysisStage
    extends GraphStage[FlowShape[PriceRatioMeasurement, PriceRatioAnalysis]] {
  val in = Inlet[PriceRatioMeasurement]("PriceRatioMeasurement.in")
  val out = Outlet[PriceRatioAnalysis]("PriceRatioAnalysis.out")

  val shape = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new PriceRatioAnalysisLogic(shape, in, out)
}

class PriceRatioAnalysisLogic(shape: Shape,
                              in: Inlet[PriceRatioMeasurement],
                              out: Outlet[PriceRatioAnalysis],
                              val windowLength: Long = 60)
    extends GraphStageLogic(shape = shape) {
  var currentWindow = List[PriceRatioMeasurement]()

  setHandler(in, new InHandler {
    override def onPush(): Unit = push(out, analyseCurrentWindow(grab(in)))
  })

  setHandler(out, new OutHandler {
    override def onPull(): Unit = pull(in)
  })

  def analyseCurrentWindow(
      measurement: PriceRatioMeasurement): PriceRatioAnalysis = {
    currentWindow = (currentWindow :+ measurement).dropWhile(
        _.timestamp < measurement.timestamp - windowLength)

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

object PriceRatioAnalyserApp extends App with PriceRatioAnalyser {
  implicit val system = ActorSystem("price-ratio-analyser")
  implicit val materializer = ActorMaterializer()

  import scala.concurrent.ExecutionContext.Implicits.global

  def printHeader() = println("""T          V       N RS      MinV    MaxV
      |---------------------------------------------""".stripMargin)

  def shutdown = Try {
    system.terminate()
    Await.result(system.whenTerminated, 10.seconds)
  }

  def analyseFile(filePath: String) =
    analyse(Paths.get(filePath))
      .runForeach(analysis => println(analysis.format))
      .onComplete(_ => shutdown)

  if (args.headOption.isDefined) {
    printHeader()
    analyseFile(args(0))
  } else {
    println("usage: price-ratio-analyser <file to analyse>")
    shutdown
  }
}

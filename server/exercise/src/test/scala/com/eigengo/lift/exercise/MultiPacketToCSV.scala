package com.eigengo.lift.exercise

import java.io._
import java.nio.BufferUnderflowException
import scodec.bits.BitVector

/**
 * Simple utility application for reading in raw binary multi-packet data, decoding it and then dumping it to a CSV file.
 * Currently, we only support decoding of accelerometer data.
 *
 * To use this utility, type the following at an sbt prompt:
 * ```
 *   project main
 *   runMain com.eigengo.lift.util.MultiPacketToCSV /path/to/fe2a035a-c0d0-47e5-8bce-370759e53885.mp /path/to/fe2a035a-c0d0-47e5-8bce-370759e53885.csv
 * ```
 */
object MultiPacketToCSV extends App {

  if (args.length != 2) {
    println("Usage: MultiPacketToCSV <input raw binary filename> <output CSV filename>")
    sys.exit(1)
  }

  val inFileName = args(0)
  val outFileName = args(1)

  // List of decoders that this utility supports
  val decoderSupport = Seq(
    AccelerometerDataDecoder,
    RotationDataDecoder
  )

  val fIn = new FileInputStream(new File(inFileName)).getChannel
  val fOut = new FileWriter(outFileName, true)

  val decoderBuffer = BitVector.fromMmap(fIn).toByteBuffer

  // List of sensors that we expect to be transmitting data
  var sensors: Option[Set[(SensorDataSourceLocation, Int)]] = None

  try {
    fOut.write("\"timestamp\",\"location\",\"rate\",\"type\",\"x\",\"y\",\"z\"\n")
    while (true) {
      for (block <- MultiPacketDecoder.decode(decoderBuffer)) {
        // Extract unique set of known sensors (we use the first multipacket to determine this)
        sensors = Some(sensors.getOrElse({
          val sensorLocations = block.packets.map(_.sourceLocation).toSet

          for {
            location <- sensorLocations
            index <- 0 until block.packets.count(_.sourceLocation == location)
          } yield (location, index)
        }))

        for ((location, index) <- sensors.get) {
          val packets = block.packets.filter(_.sourceLocation == location)
          val allBlocks = block.packets.map(p => RootSensorDataDecoder(decoderSupport: _*).decodeAll(p.payload))
          // We are able to correctly decode all sensor packet blocks
          assert(allBlocks.forall(_.isRight))
          val decodedBlocks = allBlocks.map(_.toOption.get)
          val blocks = packets.map(p => RootSensorDataDecoder(decoderSupport: _*).decodeAll(p.payload)).map(_.toOption.get)
          // All packet blocks are non-empty
          assert(decodedBlocks.nonEmpty && decodedBlocks.forall(_.nonEmpty))
          val blockTime = decodedBlocks.head.head.values.length
          // All sensor packet blocks have a common size
          assert(decodedBlocks.forall(_.forall(_.values.length == blockTime)))
          val samplingRate = decodedBlocks.head.head.samplingRate
          // All sensor packet blocks have a common sampling rate
          assert(decodedBlocks.forall(_.forall(_.samplingRate == samplingRate)))

          if (packets.isEmpty) {
            // Missing sensor data detected - so we need to emit zero padding here!
            println(s"WARNING: sensor data missing for $location.$index - zero padding from ${(block.timestamp - 1) * blockTime} - ${block.timestamp * blockTime} inserted")
            for (offset <- 0 until blockTime) {
              fOut.write(s"${(block.timestamp - 1) * blockTime + offset},$location.$index,$samplingRate,AccelerometerValue,0,0,0\n")
            }
          } else {
            // Sensor data present
            val data = blocks(index)

            data.foreach { d =>
              d.values.zipWithIndex.foreach {
                case (v: AccelerometerValue, offset) =>
                  fOut.write(s"${(block.timestamp - 1) * blockTime + offset},$location.$index,$samplingRate,AccelerometerValue,${v.x},${v.y},${v.z}\n")

                case (v: RotationValue, offset) =>
                  fOut.write(s"${(block.timestamp - 1) * blockTime + offset},$location.$index,$samplingRate,RotationValue,${v.x},${v.y},${v.z}\n")
              }
            }
          }
        }
      }
    }
  } catch {
    case exn: BufferUnderflowException =>
      // We can not parse any more multi-packets from the input file
      fOut.close()
  } finally {
    fOut.close()
  }

}

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

  try {
    fOut.write("\"timestamp\",\"location\",\"rate\",\"type\",\"x\",\"y\",\"z\"\n")
    while (true) {
      for (block <- MultiPacketDecoder.decode(decoderBuffer)) {
        for ((pkt, index) <- block.packets.zipWithIndex) {
          for (data <- RootSensorDataDecoder(decoderSupport: _*).decodeAll(pkt.payload)) {
            data.foreach { d =>
                val blockTime = d.values.length

                d.values.zipWithIndex.foreach {
                  case (v: AccelerometerValue, offset) =>
                    fOut.write(s"${(block.timestamp - 1) * blockTime + offset},${pkt.sourceLocation}.$index,${d.samplingRate},AccelerometerValue,${v.x},${v.y},${v.z}\n")

                  case (v: RotationValue, offset) =>
                    fOut.write(s"${(block.timestamp - 1) * blockTime + offset},${pkt.sourceLocation}.$index,${d.samplingRate},RotationValue,${v.x},${v.y},${v.z}\n")
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

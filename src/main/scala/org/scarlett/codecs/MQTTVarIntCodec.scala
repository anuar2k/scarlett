package org.scarlett.codecs

import scodec.bits._
import scodec._

object MQTTVarIntCodec extends Codec[Int] {
  def encode(v: Int): Attempt[BitVector] = {
    var result = BitVector.empty
    var value = v
    var encodedByte = 0
    do {
      encodedByte = value % 128
      value = value / 128
      // if there are more data to encode, set the top bit of this byte
      if (value > 0) {
        encodedByte = encodedByte | 128
      }
      result = result ++ BitVector(encodedByte.toByte)
    } while (value > 0)
    Attempt.Successful(result)
  }

  def decode(b: BitVector): Attempt[DecodeResult[Int]] = {
    var buffer = b
    var multiplier = 1
    var value = 0
    var encodedByte = 0
    do {
      // more than 3 bytes
      if (multiplier > 0x80 * 0x80 * 0x80) {
        return Attempt.Failure(Err("Malformed Remaining Length"))
      }
      encodedByte = buffer.take(8).toInt(signed = false)
      buffer = buffer.drop(8)
      value = value + (encodedByte & 0x7f) * multiplier
      multiplier *= 0x80
    } while ((encodedByte & 0x80) != 0)
    Attempt.Successful(DecodeResult(value, buffer))
  }

  def sizeBound: SizeBound = SizeBound.unknown
}

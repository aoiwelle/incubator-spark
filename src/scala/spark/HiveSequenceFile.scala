package spark

import scala.collection.mutable.ArrayBuffer

import mesos.SlaveOffer

import org.apache.hadoop.io.BytesWritable
import org.apache.hadoop.io.Text

import org.apache.hadoop.hive.serde2.`lazy`.LazyInteger

class ByteRange(val bytes: Array[Byte], val start: Int, val end: Int) {
  def this(bytes: Array[Byte], end: Int) = this(bytes, 0, end)
  
  def this(bytes: Array[Byte]) = this(bytes, 0, bytes.length)

  def this() = this(Array(), 0, 0)
  
  def length: Int = end - start

  def split(separator: Byte): Seq[ByteRange] = {
    var prevPos = start
    var pos = start
    val result = new ArrayBuffer[ByteRange]()
    while (pos < end) {
      if (bytes(pos) == separator) {
        result += new ByteRange(bytes, prevPos, pos)
        prevPos = pos + 1
      }
      pos += 1
    }
    if (prevPos < end) {
      result += new ByteRange(bytes, prevPos, pos)
    }
    return result
  }

  def decodeString: String = Text.decode(bytes, start, length)

  def decodeInt: Int = LazyInteger.parseInt(bytes, start, length)

  def decodeDouble: Double = java.lang.Double.parseDouble(decodeString)

  def encodesNull: Boolean = {
    length == 2 && bytes(start) == '\\' && bytes(start + 1) == 'N'
  }
}

object StringField {
  def unapply(bytes: ByteRange): Option[String] = {
    if (bytes == null || bytes.encodesNull)
      None
    else
      Some(bytes.decodeString)
  }
}

object IntField {
  def unapply(bytes: ByteRange): Option[Int] = {
    if (bytes == null || bytes.encodesNull)
      None
    else
      Some(bytes.decodeInt)
  }
}

object DoubleField {
  def unapply(bytes: ByteRange): Option[Double] = {
    if (bytes == null || bytes.encodesNull)
      None
    else
      Some(bytes.decodeDouble)
  }
}

object HiveSequenceFile {
  def create(sc: SparkContext, path: String, fields: Array[Int])
      : RDD[Array[ByteRange]] = {
    val records = sc.sequenceFile[BytesWritable, Text](path)
    val separator: Byte = 1
    return records.map { case (key, text) => 
      val result = new Array[ByteRange](fields.length)
      val parts = new ByteRange(text.getBytes, text.getLength).split(separator)
      for (i <- 0 until fields.length) {
        if (fields(i) < parts.length) {
          result(i) = parts(fields(i))
        }
      }
      result
    }
  }
}

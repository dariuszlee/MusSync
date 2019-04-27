package utils

import java.io.File
import java.io.PrintWriter
import scala.io.Source

object FileWriter {
  def write(text: String, path: String) {
    val writer = new PrintWriter(new File(path))
    writer.write(text)
    writer.close()
  }
}

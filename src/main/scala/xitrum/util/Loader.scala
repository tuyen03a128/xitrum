package xitrum.util

import java.io.{FileInputStream, InputStream}
import java.util.Properties

object Loader {
  private val BUFFER_SIZE = 1024
  def bytesFromInputStream(is: InputStream): Array[Byte] = {
    if (is == null) return null

    var ret = Array[Byte]()

    var buffer = new Array[Byte](BUFFER_SIZE)
    while (is.available > 0) {  // "available" is not always the exact size
      val bytesRead = is.read(buffer)
      ret = ret ++ buffer.take(bytesRead)
    }
    is.close

    ret
  }

  def bytesFromFile(path: String): Array[Byte] = {
    val is = new FileInputStream(path)
    bytesFromInputStream(is)
  }

  /**
   * @param path Relative to one of the elements in classpath, without leading "/"
   */
  def bytesFromClasspath(path: String): Array[Byte] = {
    val is = getClass.getClassLoader.getResourceAsStream(path)
    bytesFromInputStream(is)
  }

  /**
   * @param path Relative to one of the elements in classpath, without leading "/"
   */
  def stringFromClasspath(path: String): String = {
    val bytes = bytesFromClasspath(path)
    if (bytes == null) null else new String(bytes, "UTF-8")
  }

  /**
   * @param path Relative to one of the elements in classpath, without leading "/"
   */
  def propertiesFromClasspath(path: String): Properties = {
    // http://www.javaworld.com/javaworld/javaqa/2003-08/01-qa-0808-property.html?page=2
    val stream = getClass.getClassLoader.getResourceAsStream(path)

    val ret = new Properties
    ret.load(stream)
    stream.close
    ret
  }

  def propertiesFromFile(path: String): Properties = {
    val stream = new FileInputStream(path)
    val ret = new Properties
    ret.load(stream)
    stream.close
    ret
  }
}

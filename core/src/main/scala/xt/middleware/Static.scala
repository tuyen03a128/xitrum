package xt.middleware

import java.io.File
import java.io.RandomAccessFile

import scala.collection.mutable.Map

import org.jboss.netty.channel.{Channel, ChannelFuture, DefaultFileRegion, ChannelFutureListener}
import org.jboss.netty.handler.codec.http.{HttpRequest, HttpResponse, HttpHeaders}
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.ssl.SslHandler
import org.jboss.netty.handler.stream.ChunkedFile

import xt._
import xt.server.Handler

/**
 * Serves static files inside static directory.
 */
object Static {
  def wrap(app: App) = new App {
    def call(channel: Channel, request: HttpRequest, response: HttpResponse, env: Map[String, Any]) {
      val uri = request.getUri
      if (!uri.startsWith("/public"))
        app.call(channel, request, response, env)
      else {
        sanitizeUri(uri) match {
          case Some(abs) =>
            // Do not return hidden file!
            val file = new File(abs)
            if (!file.exists() || file.isHidden())
              response.setStatus(NOT_FOUND)
            else
              renderFile(abs, channel, request, response, env)

          case None =>
            response.setStatus(NOT_FOUND)
        }
      }
    }
  }

  /**
   * abs: absolute path.
   */
  def renderFile(abs: String, channel: Channel, request: HttpRequest, response: HttpResponse, env: Map[String, Any]) {
    val file = new File(abs)

    if (!file.exists() || !file.isFile()) {
      response.setStatus(NOT_FOUND)
      return
    }

    // Check
    var raf: RandomAccessFile = null
    try {
      raf = new RandomAccessFile(file, "r")
    } catch {
      case _ =>
        response.setStatus(NOT_FOUND)
        return
    }

    // Write the initial line and the header
    val fileLength = raf.length
    HttpHeaders.setContentLength(response, fileLength)
    channel.write(response)

    // Write the content
    var future: ChannelFuture = null
    if (channel.getPipeline.get(classOf[SslHandler]) != null) {
      // Cannot use zero-copy with HTTPS
      future = channel.write(new ChunkedFile(raf, 0, fileLength, 8192));
    } else {
      // No encryption - use zero-copy
      val region = new DefaultFileRegion(raf.getChannel(), 0, fileLength)
      future = channel.write(region)
      future.addListener(new ChannelFutureListener {
        def operationComplete(future: ChannelFuture) {
          region.releaseExternalResources
        }
      })
    }

    // Decide whether to close the connection or not.
    if (!HttpHeaders.isKeepAlive(request)) {
      // Close the connection when the whole content is written out.
      future.addListener(ChannelFutureListener.CLOSE)
    }

    // The Netty handler should not do anything with the response
    Handler.ignoreResponse(env)
  }

  /**
   * @return None if uri is invalid, otherwise the absolute path to the file
   */
  private def sanitizeUri(uri: String): Option[String] = {
    var decoded: String = null

    URLDecoder.decode(uri) match {
      case None => None

      case Some(decoded) =>
        // Convert file separators
        val decoded2 = decoded.replace('/', File.separatorChar)

        // Simplistic dumb security check
        if (decoded2.contains(File.separator + ".") ||
            decoded2.contains("." + File.separator) ||
            decoded2.startsWith(".")                ||
            decoded2.endsWith(".")) {
          None
        } else {
          // Convert to absolute path
          // user.dir: current working directory
          // See: http://www.java2s.com/Tutorial/Java/0120__Development/Javasystemproperties.htm
          val abs = System.getProperty("user.dir") + File.separator + decoded2

          Some(abs)
        }
    }
  }
}
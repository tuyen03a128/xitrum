package xitrum

import java.io.File
import java.nio.charset.Charset

import com.hazelcast.client.HazelcastClient
import com.hazelcast.core.{Hazelcast, HazelcastInstance}

import xitrum.scope.session.SessionStore
import xitrum.util.Loader

/** See config/xitrum.properties */
object Config extends Logger {
  /**
   * Path to the root directory of the current project.
   * If you're familiar with Rails, this is the same as Rails.root.
   * See https://github.com/ngocdaothanh/xitrum/issues/47
   */
  val root = {
    val res = getClass.getClassLoader.getResource("xitrum.properties")
    if (res != null)
      res.getFile.replace(File.separator + "config" + File.separator + "xitrum.properties", "")
    else
      System.getProperty("user.dir")  // Fallback to current working directory
  }

  //----------------------------------------------------------------------------

  /** 404.html and 500.html is used by default */
  var action404: Class[_ <: Action] = _
  var action500: Class[_ <: Action] = _

  //----------------------------------------------------------------------------

  /** See bin/runner.sh */
  val isProductionMode = (System.getProperty("xitrum.mode") == "production")

  val properties = {
    try {
      Loader.propertiesFromClasspath("xitrum.properties")
    } catch {
      case e =>
        Config.exitOnError("Could not load xitrum.properties. The \"config\" directory should be in classpath.", e)
        null
    }
  }

  //----------------------------------------------------------------------------

  val httpPorto:  Option[Int] = getOptionalProperty("http_port"). map(_.toInt)
  val httpsPorto: Option[Int] = getOptionalProperty("https_port").map(_.toInt)

  // When these are used, they must exist
  lazy val httpsKeyStore            = getPropertyWithoudDefault("https_keystore")
  lazy val httpsKeyStorePassword    = getPropertyWithoudDefault("https_keystore_password")
  lazy val httpsCertificatePassword = getPropertyWithoudDefault("https_certificate_password")

  //----------------------------------------------------------------------------

  val proxyIpso: Option[Array[String]] = getOptionalProperty("proxy_ips").map { s => s.split(",").map(_.trim)}

  val baseUri = properties.getProperty("base_uri", "")

  // Use lazy to avoid starting Hazelcast if it is not used
  // (starting Hazelcast takes several seconds, sometimes we want to work in
  // sbt console mode and don't like this overhead)
  lazy val hazelcastInstance: HazelcastInstance = {
    val hazelcastMode = getPropertyWithoudDefault("hazelcast_mode")

    // http://code.google.com/p/hazelcast/issues/detail?id=94
    // http://code.google.com/p/hazelcast/source/browse/trunk/hazelcast/src/main/java/com/hazelcast/logging/Logger.java
    System.setProperty("hazelcast.logging.type", "slf4j")

    // http://www.hazelcast.com/documentation.jsp#SuperClient
    if (hazelcastMode == "super_client")
      System.setProperty("hazelcast.super.client", "true")

    // http://code.google.com/p/hazelcast/wiki/Config
    // http://code.google.com/p/hazelcast/source/browse/trunk/hazelcast/src/main/java/com/hazelcast/config/XmlConfigBuilder.java
    if (hazelcastMode == "super_client" || hazelcastMode == "cluster_member") {
      val config = Config.root + File.separator + "config" + File.separator + "hazelcast_cluster_member_or_super_client.xml"
      System.setProperty("hazelcast.config", config)
      Hazelcast.getDefaultInstance
    } else {
      val props = Loader.propertiesFromClasspath("hazelcast_java_client.properties")
      val groupName     = props.getProperty("group_name")
      val groupPassword = props.getProperty("group_password")
      val addresses     = props.getProperty("addresses").split(",").map(_.trim)
      HazelcastClient.newHazelcastClient(groupName, groupPassword, addresses:_*)
    }
  }

  /**
   * Shutdowns Hazelcast and call System.exit(-1).
   * Once Hazelcast is started, calling System.exit(-1) does not make the stop
   * the current process!
   */
  def exitOnError(msg: String, e: Throwable) {
    logger.error(msg, e)
    Hazelcast.shutdownAll
    System.exit(-1)
  }

  //----------------------------------------------------------------------------

  val sessionStore  = {
    val className = getPropertyWithoudDefault("session_store")
    Class.forName(className).newInstance.asInstanceOf[SessionStore]
  }

  val sessionCookieName = getPropertyWithoudDefault("session_cookie_name")

  val secureKey = getPropertyWithoudDefault("secure_key")

  //----------------------------------------------------------------------------

  val maxRequestContentLengthInMB   = getPropertyWithoudDefault("max_request_content_length_in_mb").toInt

  val paramCharsetName              = getPropertyWithoudDefault("param_charset")
  val paramCharset                  = Charset.forName(paramCharsetName)

  val filteredParams                = getPropertyWithoudDefault("filtered_params").split(",").map(_.trim)

  val smallStaticFileSizeInKB       = getPropertyWithoudDefault("small_static_file_size_in_kb").toInt
  val maxCachedSmallStaticFiles     = getPropertyWithoudDefault("max_cached_small_static_files").toInt

  /**
   * Static textual files are always compressed
   * Dynamic textual responses are only compressed if they are big
   * http://code.google.com/speed/page-speed/docs/payload.html#GzipCompression
   *
   * Google recommends > 150B-1KB
   */
  val BIG_TEXTUAL_RESPONSE_SIZE_IN_KB = 1

  /**
   * In case of CPU bound, the pool size should be equal the number of cores
   * http://grizzly.java.net/nonav/docs/docbkx2.0/html/bestpractices.html
   */
  val EXECUTIORS_PER_CORE = 64

  //----------------------------------------------------------------------------

  private def getOptionalProperty(key: String): Option[String] = {
    val s = properties.getProperty(key)
    if (s == null) None else Some(s)
  }

  // For default value, use properties.getProperty(key, default) directly
  private def getPropertyWithoudDefault(key: String): String = {
    try {
      properties.getProperty(key)
    } catch {
      case e =>
        Config.exitOnError("Could not load propery \"" + key + "\" in xitrum.properties. You probably forgot to update xitrum.properties when updating Xitrum.", e)
        null
    }
  }
}

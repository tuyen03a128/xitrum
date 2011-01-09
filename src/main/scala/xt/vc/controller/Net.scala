package xt.vc.controller

import java.net.InetSocketAddress
import xt.Controller

trait Net {
  this: Controller =>

  /**
   * @return IP of the HTTP client, X-Forwarded-For is supported
   *
   * See http://en.wikipedia.org/wiki/X-Forwarded-For
   *
   * TODO: see http://github.com/pepite/Play--Netty/blob/master/src/play/modules/netty/PlayHandler.java
   *
   * TODO: inetSocketAddress can be Inet4Address or Inet6Address
   * See java.net.preferIPv6Addresses
   */
  lazy val remoteIp = {
    val inetSocketAddress = ctx.getChannel.getRemoteAddress.asInstanceOf[InetSocketAddress]
    val ip = inetSocketAddress.getAddress.getHostAddress
    ip
  }
}
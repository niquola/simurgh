(ns simurgh.server
  (:require [simurgh.parser :as parser])
  (:import
   [java.net InetAddress]
   [java.nio.charset Charset]
   [io.netty.buffer ByteBuf]
   [io.netty.bootstrap ServerBootstrap]
   [io.netty.bootstrap Bootstrap]
   [io.netty.channel ChannelFuture]
   [io.netty.channel ChannelInitializer]
   [io.netty.channel ChannelOption]
   [io.netty.channel EventLoopGroup]
   [io.netty.channel.nio NioEventLoopGroup]
   [io.netty.channel.socket SocketChannel]
   [io.netty.channel.socket.nio NioServerSocketChannel]
   [io.netty.channel.socket.nio NioDatagramChannel]
   [io.netty.channel ChannelPipeline]
   [io.netty.channel ChannelHandler]
   [io.netty.channel ChannelHandlerContext]
   [io.netty.channel ChannelInboundHandler]
   [io.netty.handler.codec ByteToMessageDecoder]
   [io.netty.channel SimpleChannelInboundHandler]))

(defn stop [server]
  (.shutdownGracefully (:master-group server))
  (.shutdownGracefully (:slave-group server)))

(defn h [cl]
  (into-array ChannelHandler [cl]))

(def utf (Charset/forName "UTF-8"))
(defn read-buf [b]
  (.toString b utf))


(defn start [handler {port :port :as opts}]
  (let [mg (NioEventLoopGroup.)
        sg (NioEventLoopGroup.)
        b  (ServerBootstrap.)]
    (-> (.group b mg sg)
        (.channel NioServerSocketChannel)
        (.childHandler
         (proxy [ChannelInitializer] []
           (initChannel [^NioDatagramChannel ch]
             (let [state (atom {:parse/state :init})]
               (-> (.pipeline ch)
                   (.addLast (h (proxy [ByteToMessageDecoder] []
                                  (channelActive [^ChannelHandlerContext ctx]
                                    (println "channelActive"))
                                  (decode [^ChannelHandlerContext ctx ^ByteBuf in out]
                                    (let [s (parser/parse @state in (fn [req] (handler ctx req)))]
                                      (reset! state s)))
                                  (exceptionCaught [^ChannelHandlerContext ctx cause]
                                    (println "Error" cause)
                                    (.close ctx))))))))))

        ;; (.option ChannelOption/SO_BACKLOG 128)
        (.childOption ChannelOption/SO_KEEPALIVE, true))
    (let [f (-> b (.bind (or port 8080)) (.sync))]
      {:master-group mg
       :start-future f
       :slave-group sg})))

(defn handler [ctx req]
  ;; (println "Req:")
  (let [body (str req) 
        bba (.getBytes body)
        s (format "HTTP/1.1 200 OK\r\nContent-Length: %s\r\n\r\n%s" (alength bba) body)
        ba (.getBytes s)
        buf (-> ctx (.alloc) (.buffer (alength ba)))]
    (.writeBytes buf ba)
    ;; (println "written" buf)
    (.writeAndFlush ctx buf)
    ;; (.close ctx)
    ))

(comment
  (def srv (start handler {:port 8889}))

  (stop srv)
  )

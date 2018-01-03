(ns simurgh.codec
  (:require [clojure.string :as str])
  (:import [io.netty.buffer ByteBuf]
           [java.nio CharBuffer]
           [java.nio.charset Charset]
           [io.netty.buffer ByteBufUtil]
           [io.netty.buffer CompositeByteBuf]
           [io.netty.buffer ByteBufAllocator]
           [io.netty.buffer ByteBufProcessor]))

;; https://github.com/netty/netty/blob/eb7f751ba519cbcab47d640cd18757f09d077b55/codec-http/src/main/java/io/netty/handler/codec/http/HttpObjectDecoder.java


(def utf (Charset/forName "UTF-8"))

(defn read-till-crlf [^ByteBuf buf]
  (let [idx (.forEachByte buf (.readerIndex buf)
                          (- (.capacity buf) (.readerIndex buf))
                          ByteBufProcessor/FIND_CRLF)]
    (cond
      (= idx (.readerIndex buf))
      (do 
        (.readByte buf)
        (.readByte buf)
        :http/end-of-headers)
      (>= idx (.readerIndex buf))
      (let [ba (byte-array (- idx (.readerIndex buf)))]
        (.readBytes buf ba)
        (.readByte buf)
        (.readByte buf)
        (String. ba utf)))))

(defn parse [{st :parse/state :as state} ^ByteBuf buf cb]
  (cond
    (= :init st)
    (if-let [req (read-till-crlf buf)]
      (let [[mth pth ver] (str/split req #"\s")]
        (recur
         (assoc state
                :http/method (keyword (str/lower-case mth))
                :http/uri pth
                :http/protocol-version ver
                :http/headers {}
                :parse/state :headers)
         buf cb))
      state)

    (= :headers st)
    (if-let [h (read-till-crlf buf)]
      (if (= :http/end-of-headers h)
        (recur (assoc state :parse/state :body) buf cb)
        (let [[nm cnt] (str/split h #":\s+" 2)]
          (recur
           (assoc-in state [:http/headers (str/lower-case nm)] cnt)
           buf cb)))
      state)

    (= :body st)
    (let [cl (when-let [i (get-in state [:http/headers "content-length"])]
               (Integer/parseInt i))]
      (recur
       (cond
         cl (if (<= cl (.readableBytes buf)) 
              (assoc state
                     :parse/state :complete
                     :http/body (.readRetainedSlice buf cl))
              (assoc state :parse/state :complete))
         :else (assoc state :parse/state :complete))
       buf cb))

    (= :complete st)
    (do (cb state)
        (assoc state :parse/state :init))

    :else state))

(defn str-to-bytebuf [^ByteBufAllocator aloc ^String s]
  (assert s)
  (ByteBufUtil/encodeString aloc (CharBuffer/wrap s) utf))

(defn response-to-http
  [^CompositeByteBuf cbb
   ^ByteBufAllocator aloc
   {{st :http/status body :http/body hs :http/headers} :http/response}]
  (let [bb (str-to-bytebuf aloc body)
        hs (assoc (or hs {}) "content-length" (str (.readableBytes bb)))
        hss (->> (mapv (fn [[k v]] (str k ": " v)) hs)
                 (str/join "\r\n"))
        s (format "HTTP/1.1 %s OK\r\n%s\r\n\r\n" (or st 200) hss)]

    (.addComponent cbb true (str-to-bytebuf aloc s))
    (.addComponent cbb true bb)

    cbb))

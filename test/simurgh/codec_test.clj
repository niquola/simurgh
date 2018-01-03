(ns simurgh.codec-test
  (:require [simurgh.codec :as sut]
            [matcho.core :as matcho]
            [clojure.test :refer :all])
  (:import [io.netty.buffer Unpooled]
           [io.netty.buffer UnpooledByteBufAllocator]))

(def messages-parts
  ["GET / HTTP/1.1\r\nHost: localhost:8888\r\nConnection: keep-alive\r\nCache-Control: max-age=0\r\nUser-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.84 Safari/537.36\r\nUpgrade-Insecure-Requests: 1\r\nAccept: text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8\r\nAccept-Encoding: gzip, deflate, br\r\nAccept-Language: en-US,en;q=0.9,ru;q=0.8,fr;q=0.7\r\nCookie: auth=%7B%22given_name%22%3A%22Nikolai%22%2C%22email%22%3A%22niquola%40gmail.com%22%2C%22aud%22%3A%22646067746089-6ujhvnv1bi8qvd7due8hdp3ob9qtcumv.apps.googleusercontent.com%22%2C%22locale%22%3A%22en%22%2C%22sub%22%3A%22109731002115757803058%22%2C%22iss%22%3A%22https%3A%2F%2Faccounts.google.com%22%2C%22name%22%3A%22Nikolai%20Ryzhikov%22%2C%22exp%22%3A1514505952%2C%22azp%22%3A%22646067746089-6ujhvnv1bi8qvd7due8hdp3ob9qtcumv.apps.googleusercontent.com%22%2C%22email_verified%22%3Atrue%2C%22family_name%22%3A%22Ryzhikov%22%2C%22id_token%22%3A%22eyJhbGciOiJSUzI1NiIsImtpZCI6ImFmMDBiY"
   "2YwNDAwNGI2MGFhNGVjZGI0YTdiNDQ0NWE3Nzk4NjMyMGQifQ.eyJhenAiOiI2NDYwNjc3NDYwODktNnVqaHZudjFiaThxdmQ3ZHVlOGhkcDNvYjlxdGN1bXYuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI2NDYwNjc3NDYwODktNnVqaHZudjFiaThxdmQ3ZHVlOGhkcDNvYjlxdGN1bXYuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMDk3MzEwMDIxMTU3NTc4MDMwNTgiLCJlbWFpbCI6Im5pcXVvbGFAZ21haWwuY29tIiwiZW1haWxfdmVyaWZpZWQiOnRydWUsIm5vbmNlIjoidXBzIiwiaXNzIjoiaHR0cHM6Ly9hY2NvdW50cy5nb29nbGUuY29tIiwianRpIjoiMmRlNzU1NTcyMjUzYTRmOGI3YjhmMWU3MTZkYmJmYjM2NWQ1ZmE1YyIsImlhdCI6MTUxNDUwMjM1MiwiZXhwIjoxNTE0NTA1OTUyLCJuYW1lIjoiTmlrb2xhaSBSeXpoaWtvdiIsInBpY3R1cmUiOiJodHRwczovL2xoNC5nb29nbGV1c2VyY29udGVudC5jb20vLVlRaUNTRV9zSmNNL0FBQUFBQUFBQUFJL0FBQUFBQUFBZnBzLzYwaUdLcjdHemtrL3M5Ni1jL3Bob3RvLmpwZyIsImdpdmVuX25hbWUiOiJOaWtvbGFpIiwiZmFtaWx5X25hbWUiOiJSeXpoaWtvdiIsImxvY2FsZSI6ImVuIn0.DH2nQe3r1zbyonSyzKePH75QJuxllFHed63cTJf5HfQhy5Os7SYQp-xtTvKqupiAciXDEb2tDHEIsX2AF2j_wsz4ic9CQDK9NKA_8d7zuwFBijX_eQ9Ln5po0IruH02wqASkWTrGqqWgjheA3LYRrNOZxJUSrN86RBciaxhpFyhxJL4JhmWosb6qloJSrJ00DAc5WRjyM19QGgMtfsDJvuMAFYB8D-bjd3EuSI9XinmovFzc1ifK56puUysuXnEUcGJP3eb7AJ0IMbHh97mIut0wxv-qIqXy7tQzrWIMQHhGVcXGzkrmEpyTUquNUFbrQYJwOWPqMGH8WxRgzXcbGA%22%2C%22jti%22%3A%222de755572253a4f8b7b8f1e716dbbfb365d5fa5c%22%2C%22picture%22%3A%22https%3A%2F%2Flh4.googleusercontent.com%2F-YQiCSE_sJcM%2FAAAAAAAAAAI%2FAAAAAAAAfps%2F60iGKr7Gzkk%2Fs96-c%2Fphoto.jpg%22%2C%22nonce%22%3A%22ups%22%2C%22iat%22%3A1514502352%7D\r\n\r\n"])

(def simple-get
  "GET /path?a=1 HTTP/1.1\r\nHost: localhost:8888\r\nUser-Agent: curl/7.54.0\r\nAccept: */*\r\n\r\n")

(def simple-post
  "POST /api/login HTTP/1.1\r\nHost: localhost:8888\r\nUser-Agent: curl/7.54.0\r\nAccept: */*\r\nContent-Type: application/json\r\nContent-Length: 35\r\n\r\n{\"username\":\"xyz\",\"password\":\"xyz\"}")

(defn to-buf [s]
  (-> s
      .getBytes
      Unpooled/wrappedBuffer))

(defn read-buf [b]
  (let [ba (byte-array (- (.writerIndex b)
                          (.readerIndex b)))]
    (.readBytes b ba)
    (String. ba sut/utf)))

(read-buf (to-buf simple-post))

(deftest test-parser

  (is (= "GET /path?a=1 HTTP/1.1" (sut/read-till-crlf (to-buf simple-get))))

  (matcho/match
   (sut/parse {:parse/state :init} (to-buf simple-get) identity)
   {:http/method :get
    :http/uri "/path?a=1"
    :http/protocol-version "HTTP/1.1"
    :http/headers {"host" "localhost:8888"
                   "user-agent" "curl/7.54.0"
                   "accept" "*/*"}})

  (matcho/match
   (sut/parse {:parse/state :init} (to-buf simple-post) identity)
   {:http/method :post
    :http/uri "/api/login"
    :http/protocol-version "HTTP/1.1"
    :http/headers {"host" "localhost:8888"
                   "user-agent" "curl/7.54.0"
                   "accept" "*/*"}
    :http/body #(= (read-buf %) "{\"username\":\"xyz\",\"password\":\"xyz\"}")})





  (sut/parse {:parse/state :init} (to-buf (nth messages-parts 0)) identity)

  (is (= "HTTP/1.1 200 OK\r\ncontent-type: text\r\ncontent-length: 5\r\n\r\nHello"
         (read-buf
          (sut/response-to-http (Unpooled/compositeBuffer)
                                (UnpooledByteBufAllocator/DEFAULT)
                                {:http/response
                                 {:http/status 200 :http/body "Hello" :http/headers {"content-type" "text"}}}))))
  
  (is (= "HTTP/1.1 200 OK\r\ncontent-type: text\r\ncontent-length: 7\r\n\r\n{\"a\":1}"
         (read-buf
          (sut/response-to-http (Unpooled/compositeBuffer)
                                (UnpooledByteBufAllocator/DEFAULT)
                                {:http/response
                                 {:http/status 200 :http/body "{\"a\":1}" :http/headers {"content-type" "text"}}}))))
  

  )


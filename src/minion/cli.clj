(ns minion.cli
  (:refer-clojure :exclude [float]))

;; ## Protocol

(defprotocol ^:private Opt
  (^:private to-opt-strings [this]))

(defn- ->short-opt
  [v]
  (if v
    (let [s (name v)]
      (if (.startsWith s "-")
        s
        (str "-" s)))))

(defn- ->long-opt
  [v]
  (if v
    (let [s (name v)]
      (if (.startsWith s "--")
        s
        (str "--" s)))))

(extend-protocol Opt
  java.lang.String
  (to-opt-strings [s]
    (if (.startsWith s "--")
      [nil s]
      [s nil]))
  clojure.lang.Keyword
  (to-opt-strings [k]
    (let [s (name k)]
      (->> (if (= (count s) 1)
             (str "-" s)
             (str "--" s))
           (to-opt-strings))))
  clojure.lang.Sequential
  (to-opt-strings [sq]
    (if (= (count sq) 1)
      (to-opt-strings (concat sq sq))
      (let [[a b] sq]
        [(->short-opt a) (->long-opt b)]))))

;; ## Helpers

(defn- add-arg
  [o a]
  (if o
    (if a
      (str o " " a)
      o)))

(defn- make-opts
  [opt & [arg]]
  (let [[short-opt long-opt] (to-opt-strings opt)]
    (assert (or (not arg) long-opt) "every switch taking a value needs a long option!")
    (->> (add-arg long-opt arg)
         (vector short-opt))))

(defn- concat-opts
  [opt arg extra [d & rst']]
  (let [[desc rst] (if (or (string? d) (nil? d))
                       [d rst']
                       [nil (cons d rst')])
        [s l] (make-opts opt arg)]
    (vec
      (concat
        [s l (str desc)]
        extra
        rst))))

(defn- string->long
  [v]
  (try
    (Long/parseLong v)
    (catch NumberFormatException ex
      (throw (Exception.  "not a valid integer/long." ex)))))

(defn- string->double
  [v]
  (try
    (let [v (Double/parseDouble v)]
      (assert (not (Double/isNaN v)))
      v)
    (catch NumberFormatException ex
      (throw (Exception.  "not a valid float/double." ex)))))

;; ## Basic Options

(defn flag
  "Create a boolean option."
  [opt & args]
  (concat-opts
    opt nil
    [:default false]
    args))

(defn disable-flag
  "Create a true-by-default flag."
  [opt k & [desc]]
  (concat-opts
    opt nil
    [:id k
     :default true
     :parse-fn not]
    [desc]))

(defn string
  "Create a string option."
  [opt arg & args]
  (concat-opts opt arg nil args))

(defn float
  "Create a float option."
  [opt arg & args]
  (concat-opts
    opt arg
    [:parse-fn string->double]
    args))

(defn integer
  "Create an integer/long option."
  [opt arg & args]
  (concat-opts
    opt arg
    [:parse-fn string->long]
    args))

(defn positive-integer
  "Create a positive integer/long option."
  [opt arg & args]
  (concat-opts
    opt arg
    [:parse-fn string->long
     :validate [pos? "must be positive."]]
    args))

(defn negative-integer
  "Create a negative integer/long option."
  [opt arg & args]
  (concat-opts
    opt arg
    [:parse-fn string->long
     :validate [neg? "must be negative."]]
    args))

(defn port
  "Create a port option."
  [opt default & args]
  (concat-opts
    opt "PORT"
    [:default default
     :parse-fn string->long
     :validate [#(< 0 % 0x10000) "must be between 0 and 65536."]]
    args))

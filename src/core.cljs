(ns ^{ :doc "Abstraction of eventual values"
       :author "Irakli Gozalishvili" }
  eventual.core)

(defprotocol ^:export IDeferred
  "Protocol for deferred values"
  (-realize [this value] "Resolves deferred with this value"))

(defprotocol ^:export IEventual
  "Protocol for eventual values"
  (-then
    [this]
    [this realized]
    [this realized rejected]
    "Returns new eventual value that is realized into
`realized(@this)` if this is promise is realized or realized into
`rejected(@this)` if this promise is rejected. If rejected is not
passed than rejection will propagate to a returned one.")
  (-when [this realized rejected]
    "Registers observers for `resolved` or `rejected` handler depending on promise state"))

;; Default implementation of IEventual for all types just fulfills
;; value with itself. Most imprtantly it provides implementation of
;; -then that every data type can get for free by just providing
;; -when.
(extend-type default
  IEventual
  (-then
    ;; If no observers registered then returned promise will be
    ;; exact equivalent so we just return this.
    ([this] this)
    ;; If rejection observer is not provided we fallback to identity.
    ;; This way rejections will propagate through the promise chain.
    ([this realize] (-then this realize identity))
    ([this realize reject]
       ;; We wrap both listeners into `attempt` that will return
       ;; rejeciton of thrown error. That way exceptions in listeners
       ;; will propaget to resulting promise.
       (let [realized (attempt realize)
             rejected (attempt reject)
             deferred (defer nil)]
         ;; when this eventual value is realized we fulfill resulting
         ;; promise into return value of a listener.
         (-when this
                #(-realize deferred (realized %))
                #(-realize deferred (rejected %)))
         ;; we return dereferrenced promise, that way if promise is
         ;; already delivered we will return actual value instead of
         ;; eventual one.
         @deferred)))
  ;; Default implementation assumes that `this` is not eventual
  ;; so we just resolve imeddiately.
  (-when [this realized _] (realized this)))

;; Implementation for nil and js/Object assume that all values are
;; non-eventual meaning that they are realized, there for they just
;; return / resolve to themselfs.
(extend-type nil
  IEventual
  (-then [this realize _] (realize this))
  (-when [this realized _] (realized this)))

;; All errors are rejections, meaning that if value was realized
;; to error it means it was rejected. There for errors reject with
;; themself.
(extend-type js/Error
  IEventual
  (-when [this _ rejected] (rejected this)))

;; We define special `Deferred` type that will implement both
;; IDeferred and IEventual protocols. Values of this type may
;; be created to represent eventual results. Like content that
;; eventually will be read form the server asynchrously. These
;; are basically promises that can be delivered using `deliver`
;; funciton only once. Dereferencing deferreds returns either
;; fulfillment value (if realized) or deferred itself if pending.
(deftype ^:export Deferred [meta state observers]
  IMeta
  (-meta [this] meta)
  IEquiv
  (-equiv [this other] (identical? this other))
  IPending
  (-realized? [this] (:done @state))
  IDeref
  (-deref [this]
    ;; FIX: if promise resolved with a promise fulfillment `value`
    ;; will be a promise so dereferencing this will return a promise
    ;; instead of expected value.
    (if (-realized? this) (:value @state) this))
  IPrintable
  (-pr-seq [this opts]
    (concat ["#<Deferred: "]
            (if (-realized? this) "realized" "pending") ">"))
  IDeferred
  (-realize [this value]
    ;; Deferreds can be realized only once.
    (if-not (-realized? this)
      (do
        (reset! state { :done true, :value value })
        ;; Once realized all observers are notified. Please note that
        ;; observers are not called instead they are registered as
        ;; observers on the fulfillment value. This way if promise is
        ;; resolved with another promise resolution value will
        ;; propagate.
        (doseq [{ realize :realize reject :reject } @observers]
          (-when value realize reject))
        (reset! observers nil)
        )))
  IEventual
  (-when [this realized rejected]
    (if (-realized? this)
      (-when @this realized rejected)
      (swap! observers conj {:realize realized, :reject rejected}))))

;; Decorator takes `f` function and wraps it so that returned
;; function returns result of (apply f ...) or an error if
;; error was thrown by `f`.
(defn attempt [f]
  (fn [& rest] (try (apply f rest)(catch js/Error error error))))

(defn ^:export defer [meta]
  (Deferred. meta (atom { :done false }) (atom [])))

;; Fufilles given deferred with a value.
(defn ^:export realize [deferred value] (-realize deferred value))

;; throws exception with a given message string.
(defn raise [message] (throw (js/Error. message)))

(defn group [promises]
  (reduce (fn [promises promise]
            (-then
             promise
             (fn [value]
               (-then
                promises
                (fn [values] (conj values value))))))
  [] promises))

;; Executes `f` with rest of the args once they are realized and
;; returns eventual value that is realized with (apply f args).
;; If any of the args is rejected rejection will propagate to the
;; returned result.
(defn ^:export ! [f & input]
  (-then (group input)
         (fn [ args ] (apply f args))))

;; Allows `f` to recover from errors. Kind of try-catch for
;; the eventual values. `f` is called only if eventual is
;; rejected passing a rejeciton reason to `f`. Returned
;; promise will be realized to whatever `f` will return once
;; called. If eventual is not rejected that returned value
;; will be  identical to given `eventual`.
(defn ^:export ?! [f eventual] (-then eventual identity f))
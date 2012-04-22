# eventual

Abstraction of eventual values for clojurescript.

## Examples

```clojure

;; make an eventual value `a`
(def a (defer))                                         ;; #<Deferred: pending>

;; make eventual result `b` of (+ a 2)
(def b (! + a 2))                                       ;; #<Deferred: pending>

;; make eventual result `c` of (+ b a 17)
(def c (! + b a 17))                                    ;; #<Deferred: pending>

;; once `c` is realized print it 
(! print c)                                             ;; #<Deferred: pending>

;; realize `a` with `11`
(realize a 11)

;; eventually prints
41

;; errors / rejections propagate through promise chains

;; make eventual value for (raise "Boom!") that throws exception 
(def e (! raise "Boom!"))                               ;; #<Error: Boom!>
(def a2 (! + e 13))                                     ;; #<Error: Boom!>
(def b2 (! + a2 7))                                     ;; #<Error: Boom!>

(?! #(print "Failed:" %) b2)                            ;; nil

;; Will print
Error: Boom!

;; Also eventual errors are supported

(def e2 (defer))                                        ;; #<Deferred: pending>
(def a3 (! + e2 11))                                    ;; #<Deferred: pending>
(def b3 (! - a2 a3))                                    ;; #<Deferred: pending>

;; handle error and recover

(def r (?! #(do (print "Recovering from: " %) 17) b3))  ;; #<Deferred: pending>

;; print `r` once resolved
(! print r)                                             ;; #<Deferred: pending>

;; reject into e2
(realize e2 (js/Error. "Brax"))

;; prints
Recovering from:  #<Error: Brax>
17
```

## License

Copyright (C) 2012 Irakli Gozalishvili

Distributed under the Eclipse Public License, the same as Clojure.

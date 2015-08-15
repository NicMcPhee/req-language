(ns req.core-test
  (:use midje.sweet)
  (:use [req.core])
  )

;; queues

(fact "new-queue will populate the queue with the seq it's handed"
  (count (new-queue [1 2 3 4])) => 4
  (peek (new-queue '(1 2 3 4))) => 1
  (new-queue [:a :b :a :b]) => (just [:a :b :a :b])
  )

(fact "conjing to a queue adds at the right"
  (rest (conj (new-queue [1 2 3 4]) 99)) => (just [2 3 4 99])
  )

(fact "popping from a queue removes from the left"
  (pop (new-queue [1 2 3 4])) => (just [2 3 4])
  )

;; interpreter

(fact "calling req-with with a vector of items puts those onto the queue"
  (:queue (req-with [2 5 8])) => (just [2 5 8])
  (:queue (req-with [2 [3] false])) => (just [2 [3] false])
  )

;; step: literals

(fact "calling step on an interpreter containing only literals will cycle them"
  (:queue (step (req-with [false 1.2 3]))) => (just [1.2 3 false])
  (:queue (step (step (req-with [false 1.2 3])))) => (just [3 false 1.2])
  )

;; step: imperatives

(fact "when :dup is executed, the top item on the queue is doubled and sent to the tail"
  (:queue (step (req-with [:dup 1.2 3]))) => (just [1.2 3 1.2])
  (count (:queue (step (req-with [:dup])))) => 0
  )

(fact "when :pop is executed, the top item on the queue is thrown away"
  (:queue (step (req-with [:pop 1 2 3 4 5]))) => (just [2 3 4 5])
  (count (:queue (step (req-with [:pop 2])))) => 0
  (count (:queue (step (req-with [:pop])))) => 0
  )

(fact "when :swap is executed, the top two items on the queue are switched (and sent to the end)"
  (:queue (step (req-with [:swap 1 2 3 4 5]))) => (just [3 4 5 2 1])
  (:queue (step (req-with [:swap 1 2]))) => (just [2 1])
  (:queue (step (req-with [:swap 1]))) => (just [1])
  (:queue (step (req-with [:swap]))) => (just [])
  )

(fact "when :archive is executed, the entire queue is duplicated at its own tail"
  (:queue (step (req-with [:archive 1 2 3 4 5]))) => (just [1 2 3 4 5 1 2 3 4 5])
  (:queue (step (req-with [:archive]))) => (just [])
  )

(fact "when :reverse is executed, the entire queue is flipped head-to-tail"
  (:queue (step (req-with [:reverse 1 2 3 4 5]))) => (just [5 4 3 2 1])
  (:queue (step (req-with [:reverse]))) => (just [])
  )

(fact "when :flush is executed, the entire queue is emptied"
  (:queue (step (req-with [:flush 1 2 3 4 5]))) => (just [])
  (:queue (step (req-with [:flush]))) => (just [])
  )

(fact "when :next is executed, the top item is sent to the tail"
  (:queue (step (req-with [:next 1 2 3 4 5]))) => (just [2 3 4 5 1])
  (:queue (step (req-with [:next 1]))) => (just [1])
  (:queue (step (req-with [:next]))) => (just [])
  )

(fact "when :prev is executed, the tail item is sent to the head"
  (:queue (step (req-with [:prev 1 2 3 4 5]))) => (just [5 1 2 3 4])
  (:queue (step (req-with [:prev 1]))) => (just [1])
  (:queue (step (req-with [:prev]))) => (just [])
  )


;; in-place closure design


;; closures

(defrecord Qlosure [as-str wants bindings next-stage])

(def adder
  (let [bindings {:lhs 77}]
    (Qlosure.
      "77 + «num»"
      {:rhs number?}
      bindings
      (partial + (:lhs bindings))
    )))

(def wants {:arg2 number? :arg3 integer?})

(fact "a handshake function is possible"
  (keys wants) => (just [:arg2 :arg3])
  (keys (filter #((second %) 6.2) wants)) => (just [:arg2])
  (keys (filter #((second %) false) wants)) => nil
  (keys (filter #((second %) 6.2) (:wants adder))) => (just [:rhs])
  )

(defn can-use-this-thing? [qlosure item]
  (let [w (:wants qlosure)]
    (some? (keys (filter #((second %) item) w)))
    )
  )

(fact "a handshake function can be used"
  (can-use-this-thing? adder 6.2) => true
  (can-use-this-thing? adder [1 2 3]) => false
  (can-use-this-thing? adder "foo") => false
  )

(defn ways-to-use-this-thing [qlosure item]
  (let [w (:wants qlosure)]
    (into [] (keys (filter #((second %) item) w)))
    )
  )

(fact "a wants list can be produced"
  (ways-to-use-this-thing adder 6.2) => (just [:rhs])
  (ways-to-use-this-thing adder [1 2 3]) => (just [])
  (ways-to-use-this-thing adder "foo") => (just [])
  )

(fact "a qlosure can do this thing"
  (apply (:next-stage adder) [11]) => 88)


(defmulti req-plus class)
(defmethod req-plus java.lang.Long [i] :i-haz-a-integer)
(defmethod req-plus java.lang.Double [f] :i-haz-a-float)
(defmethod req-plus clojure.lang.Ratio [r] :hey-a-fraction)
(defmethod req-plus :default [x] :not-a-thing-I-like)

(fact "Multimethods can recognize stuff"
  (req-plus 8) => :i-haz-a-integer
  (req-plus 2.3) => :i-haz-a-float
  (req-plus false) => :not-a-thing-I-like
  (req-plus 3/7) => :hey-a-fraction
  )

(fact "understanding isa?"
  (isa? java.lang.Long java.lang.Number) => true
  (isa? java.lang.Long java.lang.Boolean) => false
  (isa? java.lang.Long java.lang.Object) => true
  (isa? java.lang.Long java.lang.Double) => false
  )
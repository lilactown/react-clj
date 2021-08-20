(ns lilactown.react.fiber
  (:require
   [clojure.zip :as zip]))

;;
;; Types and protocols
;;


(defprotocol IRender
  (render [c props] "Render a component, return a tree of immutable elements"))


(defrecord Element [type props key])


(defrecord FiberNode [type props state children])


(defn element?
  [x]
  (= Element (type x)))


(defn element-type?
  [x]
  (or (= Element (type x))
      (string? x)
      (number? x)))


(defn fiber?
  [x]
  (= FiberNode (type x)))


;;
;; Zipper setup
;;


(defn make-fiber
  [fiber children]
  (if (or (fiber? fiber)
          (element? fiber))
    (->FiberNode
     (:type fiber)
     (:props fiber)
     (:state fiber)
     children)
    fiber))


(defn root-fiber
  [el]
  (->FiberNode :root {:children [el]} nil nil))


(defn fiber-zipper
  [fiber]
  (zip/zipper
   fiber?
   :children
   make-fiber
   fiber))


;;
;; Hooks
;;


(declare ^:dynamic *hooks-context*)


(defn hooks-context
  [previous-state]
  {:state (atom {:index 0
                 :previous previous-state
                 :current []}) })


(defn set-current-hook-state!
  [ctx state]
  (swap! (:state ctx)
         (fn [hooks-state]
           (-> hooks-state
               (update :current conj state)
               (update :index inc)))))


(defn use-state
  [init]
  (let [context *hooks-context*
        {:keys [index previous]} @(:state context)
        ;; TODO allow implementation to be swapped in here
        state (or (nth previous index)
                  [init (fn [& _])])]
    (set-current-hook-state! context state)
    state))


(defn use-effect
  [f deps]
  (let [context *hooks-context*
        {:keys [index previous]} @(:state context)
        prev-state (nth previous index)
        state [f deps]]
    ;; deps not=
    (when (not= (second prev-state) deps)
      ;; TODO schedule effect using impl TBD
      nil)
    (set-current-hook-state! context state)
    nil))



;;
;; Reconciliation
;;


(defn perform-work
  "Renders the fiber, returning child elements"
  [{:keys [type props] :as _node}]
  (cond
    (satisfies? IRender type)
    [(render type props)]

    ;; destructuring doesn't seem to fail if `_node` is actually a primitive
    ;; i.e. a string or number, so we can just check to see if `type` is
    ;; `nil` to know whether we are dealing with an actual element
    (some? type)
    (flatten (:children props))

    :else nil))


(defn reconcile-node
  [loc]
  (let [node (zip/node loc)
        hooks-context (hooks-context (-> node :previous :state))
        results (binding [*hooks-context* hooks-context]
                  (perform-work node))
        node' (zip/make-node
               loc
               (if (map? node)
                 (assoc node :state (-> hooks-context :state deref :current))
                 node)
               results)]
    (zip/replace loc node')))


(defn reconcile
  [fiber]
  (loop [loc (fiber-zipper fiber)]
    (if (zip/end? loc)
      (zip/root loc)
      (recur (zip/next (reconcile-node loc))))))


;;
;; example
;;


(defn $
  ([t] (->Element t nil nil))
  ([t arg]
   (if-not (element-type? arg)
     (->Element t arg (:key arg))
     (->Element t {:children (list arg)} nil)))
  ([t arg & args]
   (if-not (element-type? arg)
     (->Element t (assoc arg :children args) (:key arg))
     (->Element t {:children (cons arg args)} nil))))


(extend-type clojure.lang.Fn
  IRender
  (render [f props] (f props)))


(defn greeting
  [{:keys [user-name]}]
  ($ "div"
     {:class "greeting"}
     "Hello, " user-name "!"))


(defn counter
  [_]
  ($ "div"
     ($ "button" "+")
     (for [n (range 4)]
       ($ "div" {:key n} n))))


(defn app
  [_]
  (let [[user-name set-user-name] (use-state "Will")]
    ($ "div"
       {:class "app container"}
       ($ "h1" "App title")
       "foo"
       ($ greeting {:user-name user-name})
       ($ "input" {:value user-name :on-change #(set-user-name)})
       ($ counter))))


(reconcile (root-fiber ($ app)))

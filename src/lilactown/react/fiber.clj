(ns lilactown.react.fiber
  (:require
   [clojure.zip :as zip]))

;;
;; Types and protocols
;;


(defprotocol IRender
  (render [c props] "Render a component, return a tree of immutable elements"))


(defrecord Element [type props key])


(defrecord FiberNode [alternate type props state children])


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
     (:alternate fiber)
     (:type fiber)
     (:props fiber)
     (:state fiber)
     children)
    fiber))


(defn root-fiber
  [alternate el]
  (->FiberNode alternate :root {:children [el]} nil nil))


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
  [alternate-state]
  {:state (atom {:index 0
                 :previous alternate-state
                 :current []})})


(defn get-previous-hook-state!
  [ctx]
  (let [{:keys [index previous]} @(:state ctx)]
    (nth previous index)))


(defn set-current-hook-state!
  [ctx state]
  (swap! (:state ctx)
         (fn [hooks-state]
           (-> hooks-state
               (update :current conj state)
               (update :index inc)))))


(defn use-ref
  [init]
  (let [ctx *hooks-context*]
    (or (get-previous-hook-state! ctx)
        (doto (atom init)
          (->> (set-current-hook-state! ctx))))))


(defn use-memo
  [f deps]
  (let [ctx *hooks-context*
        [_ prev-deps :as prev-state] (get-previous-hook-state! ctx)]
    (if (not= prev-deps deps)
      (let [v (f)
            state [v deps]]
        (set-current-hook-state! ctx state)
        state)
      prev-state)))


(defn use-callback
  [f deps]
  (use-memo #(f) deps))


(defn use-reducer
  ([f initial]
   (use-reducer f initial identity))
  ([_f initial init-fn]
   (let [ctx *hooks-context*
         state (or (get-previous-hook-state! ctx)
                   ;; TODO allow implementation to be swapped in here
                   [(init-fn initial) (fn [& _])])]
     (set-current-hook-state! ctx state)
     state)))


(defn use-state
  [init]
  (let [[state dispatch] (use-reducer
                          (fn [state [arg & args]]
                            (if (ifn? arg)
                              (apply arg state args)
                              arg))
                          init)
        set-state (use-callback
                   (fn [& args]
                     (dispatch args))
                   [])]
    [state set-state]))


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


(defn use-layout-effect
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
  [node host-config]
  (let [hooks-context (hooks-context (-> node :previous :state))
        results (binding [*hooks-context* hooks-context]
                  (perform-work node))]
    (make-fiber
     (if (map? node)
       (assoc node :state (-> hooks-context :state deref :current))
       node)
     results)))


(defn reconcile
  [fiber host-config]
  (loop [loc (fiber-zipper fiber)]
    (if (zip/end? loc)
      (zip/root loc)
      (recur (zip/next (zip/edit loc reconcile-node host-config))))))


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
  (let [[count set-count] (use-state 4)]
    ($ "div"
      ($ "button" {:on-click #(set-count inc)} "+")
      (for [n (range count)]
        ($ "div" {:key n} n)))))


(defn app
  [{:keys [user-name]}]
  ($ "div"
     {:class "app container"}
     ($ "h1" "App title")
     "foo"
     ($ greeting {:user-name user-name})
     ($ counter)))


(def fiber0
  (reconcile
   (root-fiber nil ($ app {:user-name "Will"}))
   {}))


#_(reconcile
 (root-fiber fiber0 ($ app {:user-name "Alan"}))
 {})

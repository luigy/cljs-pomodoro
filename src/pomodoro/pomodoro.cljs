(ns pomodoro.pomodoro
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as string]))


(enable-console-print!)

; START time intervals
(def ^:constant one-min (* 1e3 60))
(def ^:constant presets {:five (* one-min 5)
                         :twenty-five (* one-min 25)})
; END time intervals

;; Must extend number for js/Number since time
;; is represented as milliseconds
(extend-type number
  ICloneable
  (-clone [n] (js/Number. n)))

(extend-type boolean
  ICloneable
  (-clone [b] (js/Boolean. b)))

(defn now [] (.now js/Date))

(defn default-state []
  (let [now (now)]
    {:stime now
     :etime (+ (:twenty-five presets) now)
     :orig-time now
     :on false}))

(def sound (.createElement js/document "audio"))
(set! (.-src sound) "/sounds/bell.mp3")
(.load sound)

(defn play-sound []
  (.load sound)
  (.play sound)
  sound)

(defn expired? [stime etime]
  (<= (- etime stime) 0))

; START app-state
(def app-state (atom (default-state)))
; END app-state

; START format-time
(defn format-time
  "Format time as min:sec"
  [d]
  (let [min (.getMinutes d)
        sec (.getSeconds d)
        formatted (map #(if (< % 10) (str "0" %) %) [min sec])]
    (string/join ":" formatted)))
; END format-time

; START display-time multi method
(defmulti display-time (fn [d] (type d)))
(defmethod display-time js/Number [d] (format-time (js/Date. d)))
(defmethod display-time js/Date [d] (format-time d))
; END display-time

(defn preset-item [{:keys [on] :as cursor} owner]
  (reify
    om/IRender
    (render [_]
      (dom/li
        #js {:className (if (.valueOf on) "preset disabled" "preset")}
        (dom/a #js {:onClick (fn [e]
                               (.preventDefault e)
                               (when-not (.valueOf on)
                                 (let [t (om/get-state owner :time)
                                       ds (default-state)]
                                   (om/transact! cursor
                                                 #(assoc (into % ds)
                                                         :etime (+ (* one-min t) (:stime ds)))))))}
               (om/get-state owner :time))))))

(defn presets-view [{:keys [stime etime on] :as cursor} _]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "navbar-collapse"}
               (dom/ul #js {:className "nav navbar-nav navbar-right"}
                       (om/build preset-item cursor {:init-state {:time 1}})
                       (om/build preset-item cursor {:init-state {:time 5}})
                       (om/build preset-item cursor {:init-state {:time 25}}))))))

(defn header-view [{:keys [stime etime on] :as cursor} _]
  (reify
    om/IRender
    (render [_]
      (dom/nav #js {:className "navbar navbar-default"}
               (dom/div #js {:className "navbar-header"}
                        (dom/img #js {:className "navbar-left navbar-text"
                                      :src "/images/pom-sm.png"
                                      :alt "pomodoro"})
                        (dom/a #js {:className "navbar-brand"
                                    :href="http://pomodoro.trevorlandau.net"} "pOModoro"))
               (om/build presets-view cursor)))))

(defn timer-view [{:keys [stime etime on]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:width "100%"})

    om/IWillUpdate
    (will-update [_ {:keys [stime etime orig-time]} _]
      (om/set-state! owner :width (-> (/ (- etime stime) (- etime orig-time))
                                      (* 100)
                                      (str "%"))))

    om/IRenderState
    (render-state [_ {:keys [width]}]
      (dom/div
        #js {:className (let [s  "progress"]
                          (if (.valueOf on)
                            (str s " progress-striped active")
                            s))}
        (dom/div
          #js {:className "progress-bar progress-bar-danger"
               :style #js {:width width}}
          (display-time (- etime stime)))))))

(defn actions-view [{:keys [stime etime on] :as cursor} _]
  (reify
    om/IRender
    (render [_]
      (dom/div
        nil
        (dom/div #js {:className "center-block btn-group"}
                 (dom/button
                   #js {:type "button"
                        :className "btn btn-sm btn-default"
                        :onClick (fn []
                                   (om/transact! on #(not (.valueOf on))))}
                   (if (.valueOf on) "Pause" "Resume"))

                 (dom/button
                   #js {:type "button"
                        :className "btn btn-sm btn-default"
                        :onClick #(when-not (.valueOf on)
                                    (om/transact! cursor default-state))
                        :disabled (.valueOf on)}
                   "Reset"))

        (dom/div #js {:className "pull-right info-bar"}
                 (dom/a #js {:href "https://github.com/landau/cljs-pomodoro"
                             :target "_blank"}
                        (dom/i #js {:className "fa fa-github-square"} ""))

                 (dom/a #js {:href "https://twitter.com/trevor_landau"
                             :target "_blank"}
                        (dom/i #js {:className "fa fa-twitter-square"} ""))

                 (dom/a #js {:href "http://en.wikipedia.org/wiki/Pomodoro_Technique"
                             :target "_blank"}
                        (dom/i #js {:className "fa fa-question-circle"} "")))))))

(defn can-update [{:keys [stime etime on]}]
  (and (.valueOf on) (> (- etime stime) 0)))

; START pom-view
(defn pom-view [{:keys [stime etime on] :as cursor} owner]
  (reify
    om/IDisplayName
    (display-name [_] "pom-view")

    ;; Update timer and app state here
    om/IWillMount
    (will-mount [_]
      (js/setInterval
        (fn []
          (om/transact! cursor #(if (can-update %)
                               (assoc % :stime (+ 1e3 (:stime %)))
                               %)))
        1e3))

    om/IWillUpdate
    (will-update [_ {:keys [etime stime]} _]
      ;; If time is zero then set on to false
      (when (expired? stime etime)
        (om/transact! on #(identity false))
        (play-sound)))

    om/IRender
    (render [_]
      (dom/div
        #js {:className "timer-body"}
        (om/build header-view cursor)
        (om/build timer-view cursor)
        (om/build actions-view cursor)))))
; END pom-view

(om/root pom-view app-state {:target (. js/document (getElementById "app"))})

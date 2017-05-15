(defproject cashday "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.229"]
                 [proto-repl "0.3.1"]
                 [com.stuartsierra/component "0.3.2"]
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [com.datomic/datomic-pro "0.9.5561"
                  :exclusions [com.google.guava/guava]]
                 [clj-time "0.13.0"]
                 [io.pedestal/pedestal.service "0.5.2"]
                 [io.pedestal/pedestal.jetty "0.5.2"]
                 [io.pedestal/pedestal.immutant "0.5.2"]
                 [io.pedestal/pedestal.tomcat "0.5.2"]
                 [org.clojure/data.csv "0.1.3"]
                 [vvvvalvalval/datofu "0.1.0"]

                 ;; cljs
                 [reagent "0.6.0"]
                 [re-frame "0.9.2"]
                 [day8.re-frame/http-fx "0.1.3"]
                 [cljsjs/moment "2.17.1-0"]
                 [cljsjs/toastr "2.1.2-0"]
                 [cljsjs/semantic-ui "2.2.4-0"]
                 [cljsjs/pikaday "1.5.1-2"]
                 [cljsjs/react-draggable "2.2.3-0"]
                 [cljsjs/jquery-ui "1.11.4-0"]]

  :plugins [[lein-cljsbuild "1.1.4"]
            [lein-environ "0.4.0"]]

  :target-path "target/%s"

  :main cashday.core

  :min-lein-version "2.5.3"

  :source-paths ["src/clj" "src/cljs"]
  :resource-paths ["config" "resources"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :figwheel {:css-dirs ["resources/public/css"]
             :nrepl-port 7002
             :nrepl-middleware ["cemerick.piggieback/wrap-cljs-repl"]}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs"]
     :figwheel     {:on-jsload "cashday.core/mount-root"}
     :compiler     {:main                 cashday.core
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload]
                    :external-config      {:devtools/config {:features-to-install :all}}}}


    {:id           "min"
     :source-paths ["src/cljs"]
     :compiler     {:main            cashday.core
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :pseudo-names    true
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}]}

  :profiles
  {:dev
   {; не нужно, если надо чтобы lein repl работал
    ; :repl-options {:init-ns cashday.repl
    ;                :nrepl-middleware ["cemeric.piggieback/wrap-cljs-repl"]}
    :source-paths ["dev"]
    :main user
    :dependencies [[binaryage/devtools "0.8.2"]
                   [com.cemerick/piggieback "0.2.1"]
                   [org.clojure/tools.nrepl "0.2.10"]]
    :plugins      [[lein-figwheel "0.5.9"]]}

   :uberjar
   {:uberjar-name "cashday.jar"
    :source-paths ^:replace ["src/clj"]
    :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
    :aot :all}})

(defproject bookmarks "1.0.0-SNAPSHOT"
  :description "An example karras web service"
  :dependencies [[clojure "1.2.0-master-SNAPSHOT"]
                 [clojure-contrib "1.2.0-SNAPSHOT"]
                 [compojure "0.4.1"]
                 [karras "0.4.0-SNAPSHOT"]
                 [robert/hooke "1.0.2"]
                 [ring/ring-jetty-adapter "0.2.5"]]
  :dev-dependencies [[swank-clojure "1.2.1"]]
  :namespaces [bookmarks.core])

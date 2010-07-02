(ns bookmarks.core
  (:require [karras.core :as k]
            [karras.collection :as c])
  (:use [clojure.contrib.json :only [json-str Write-JSON]]
        [compojure.core :only [defroutes GET POST PUT DELETE routes]]
        [ring.adapter.jetty :only [run-jetty]]
        [ring.middleware.keyword-params :only [wrap-keyword-params]]
        karras.entity
        karras.sugar
        karras.validations))

(extend org.bson.types.ObjectId Write-JSON
        {:write-json (fn [x out] (.print out (str "\"" x "\"")))})

(defentity User
  [:name :full-name :email :password]
  (index (asc :name)))

(defentity Bookmark
  [:user-id {:type :reference :of User}
   :uri
   :uri-hash
   :short-description
   :long-description
   :timestamp
   :public?
   :tags {:type :list :of String}]
  (index (asc :uri-hash))
  (index (asc :tags))
  make-validatable
  (validates-pressence-of :uri))

(defn fetch-by-username [username]
  (fetch-one User (where (eq :name username))))

(defn search-user-bookmarks [user-id & where-clauses]
  (fetch Bookmark
         (apply where (eq :user-id user-id) where-clauses)))

(defn fetch-users-tags [user-id]
  (set (mapcat :tags (fetch Bookmark
                            (where (eq :user-id user-id))
                            :include [:tags]))))

(defn read-params-for [type params]
  (reduce (fn [result [k _]] (if (params (name k))
                               (assoc result k (params (name k)))
                               result))
          {} (:fields (entity-spec type))))

(defmacro json-respond [form]
  `(try
     {:status 200 :body (json-str ~form)}
     (catch Exception e#
       (.printStackTrace e#)
       {:status 500 :body (json-str {:type (class e#)
                                     :message (.getMessage e#)})})))

(defn read-objectid [request]
  (org.bson.types.ObjectId. (get (:params request) "id")))

(defn list-resources 
  "GET /{collection-name}"
  [request type]
  (json-respond (fetch-all type)))

(defn create-resource 
  "POST /{collection-name}"
  [request type]
  (json-respond (create type (read-params-for type (:params request)))))

(defn get-resource
  "GET /{collection-name}/:id"
  [request type]
  (json-respond (fetch-one type
                           (where (eq :_id (read-objectid request))))))

(defn update-resource
  "PUT /{collection-name}/:id"
  [request type]
  (json-respond (c/update (collection-for type)
                          (where (eq :_id (read-objectid request)))
                          (modify (set-fields (read-params-for type (:params request)))))))

(defn delete-resource
  "DELETE /{collection-name}/:id"
  [request type]
  (json-respond (c/delete (collection-for type)
                          (where (eq :_id (read-objectid request))))))

(defmacro defresource [type & options]
  (let [name (:collection-name (entity-spec (resolve type)))
        nsym (symbol (str name "-routes"))]
    `(defroutes ~nsym
       (GET (str "/" ~name) req# (list-resources req# ~type))
       (POST (str "/" ~name) req# (create-resource req# ~type))
       (GET (str "/" ~name "/:id") req# (get-resource req# ~type))
       (PUT (str "/" ~name "/:id") req# (update-resource req# ~type))
       (DELETE (str "/" ~name "/:id") req# (delete-resource req# ~type)))))

(defonce bookmark-db (k/mongo-db :karras-bookmark))

(defn wrap-mongo-request [app]
  (fn [request]
    (k/with-mongo-request bookmark-db
      (app request))))

(defresource Bookmark)
(defresource User)

(def app
     (-> (routes bookmarks-routes users-routes)
         wrap-keyword-params
         wrap-mongo-request))

(defn -main [& args]
  (let [[port] args]
    (run-jetty (var app)
               {:port (Integer. (or port 8080))
                :join? false})))

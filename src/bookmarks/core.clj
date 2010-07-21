(ns bookmarks.core
  (:require [karras.core :as k]
            [karras.collection :as c])
  (:use [clojure.contrib.json :only [json-str Write-JSON]]
        [net.cgrand.moustache :only [app]]
        [ring.adapter.jetty :only [run-jetty]]
        [ring.middleware.keyword-params :only [wrap-keyword-params]]
        [ring.middleware.params :only [wrap-params]]
        karras.entity
        karras.sugar
        clojure.pprint)
  (:import [org.bson.types ObjectId]))

(extend ObjectId Write-JSON
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
  (index (asc :tags)))

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

(defn list-resources 
  "GET /{collection-name}"
  [type]
  (fn [request]
    (json-respond (fetch-all type))))

(defn create-resource 
  "POST /{collection-name}"
  [type]
  (fn [request]
    (json-respond (create type (read-params-for type (:params request))))))

(defn get-resource
  "GET /{collection-name}/:id"
  [type id]
  (fn [request]
    (json-respond (fetch-one type
                             (where (eq :_id (ObjectId. id)))))))

(defn update-resource
  "PUT /{collection-name}/:id"
  [type id]
  (fn [request]
    (json-respond (find-and-modify type
                                   (where (eq :_id (ObjectId. id)))
                                   (modify (set-fields (read-params-for
                                                        type (:params request))))
                                   :return-new true))))

(defn delete-resource
  "DELETE /{collection-name}/:id"
  [type id]
  (fn [request]
    (json-respond (find-and-remove type
                                   (where (eq :_id (ObjectId. id)))))))

(defn resource-app [type & options]
  (app [] {:get (list-resources type)
           :post (create-resource type)}
       [id] {:get (get-resource type id)
             :put (update-resource type id)
             :delete (delete-resource type id)}))

(defonce bookmark-db (k/mongo-db :karras-bookmark))

(defn wrap-mongo-request [handler]
  (fn [request]
    (k/with-mongo-request bookmark-db
      (handler request))))

(def application
     (-> (app ["bookmarks" &] (resource-app Bookmark)
              ["users" &] (resource-app User))
         wrap-params
         wrap-keyword-params
         wrap-mongo-request))

(defn -main [& args]
  (let [[port] args]
    (run-jetty (var application)
               {:port (Integer. (or port 8080))
                :join? false})))

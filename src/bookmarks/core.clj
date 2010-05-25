(ns bookmarks.core
  (:require karras)
  (:use karras.document
        karras.sugar))

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

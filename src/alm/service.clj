(ns alm.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [ring.util.response :as ring-resp]
              [alm.peer :as peer]
              [hiccup.core :as hiccup]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s" (clojure-version))))

(defn home-page
  [request]
  (ring-resp/response
   (hiccup/html
    [:p "Catalogs"
     [:ul
      (for [catalog (peer/get-catalogs)]
        (let [[id name] catalog]
          [:li [:p name
                [:a {:href (str "/edit-catalog-fields?catalog-id=" id)} "Edit Fields"]
                [:a {:href (str "/view-parts?catalog-id=" id)} "View Parts"]
                [:a {:href (str "/new-part?catalog-id=" id)} "New Part"]]]))]
     [:a {:href "/new-catalog"} "Create Catalog"]]
    [:p "Fields"
     [:ul
      (for [field (peer/get-fields)]
        [:li (second field)])]
     [:a {:href "/new-field"} "Create Field"]])))

(defn new-part
  [{params :query-params}]
  (let [catalog-id-str (:catalog-id params)
        catalog-id (Long/parseLong catalog-id-str)
        [catalog-name fields] (peer/get-catalog-with-fields catalog-id)]
    (ring-resp/response
     (hiccup/html
      [:p catalog-name]
      [:form {:action "add-part" :method "post"}
       (for [[field-id field-name] fields]
         [:p
          [:td field-name]
          [:input {:name field-name}]])
       [:input {:name "catalog-id" :value catalog-id}]
       [:input {:type "submit"}]]))))

(defn edit-part
  [{params :query-params}]
  (let [part-id-str (:part-id params)
        part-id (Long/parseLong part-id-str)
        part (peer/get-part part-id)
        catalog-eid (:part/catalog part)
        catalog-id (:db/id catalog-eid)
        [catalog-name fields] (peer/get-catalog-with-fields catalog-id)]
    (ring-resp/response
     (hiccup/html
      [:p catalog-name]
      [:form {:action "update-part" :method "post"}
       (for [[field-id field-name] fields]
         [:p
          [:td field-name]
          [:input {:name field-name :value (field-name part)}]])
       [:input {:name "part-id" :value part-id}]
       [:input {:type "submit"}]]))))

(defn add-part
  [{params :form-params}]
  (let [field-params (dissoc params "catalog-id")
        catalog-id-str (params "catalog-id")
        catalog-id (Long/parseLong catalog-id-str)
        field-map (into {} (for [[k v] field-params] [(keyword (str "field/" k)) v]))]
    (peer/add-part catalog-id field-map)
    (ring-resp/redirect-after-post "/")))

(defn update-part
  [{params :form-params}]
  (let [field-params (dissoc params "part-id")
        part-id-str (params "part-id")
        part-id (Long/parseLong part-id-str)
        field-map (into {} (for [[k v] field-params] [(keyword (str "field/" k)) v]))]
    (peer/update-part part-id field-map)
    (ring-resp/redirect-after-post "/")))

(defn view-parts
  [{params :query-params}]
  (let [catalog-id-str (:catalog-id params)
        catalog-id (Long/parseLong catalog-id-str)
        [catalog-name fields] (peer/get-catalog-with-fields catalog-id)]
    (ring-resp/response
     (hiccup/html
      [:p catalog-name]
      [:table
       [:tr
        (for [[field-id field-name] fields]
          [:td field-name])
        [:td "Edit"]]
       (for [part (peer/get-parts catalog-id)]
         [:tr
          (for [[field-id field-name] fields]
            [:td (field-name part)])
          [:td [:a {:href (str "/edit-part?part-id=" (:id part))} "Edit Part"]]])]))))

(defn new-catalog
  [request]
  (ring-resp/response
   (hiccup/html
    [:p "New Catalog"]
    [:form {:action "add-catalog" :method "post"}
     [:input {:name "name"}]
     [:input {:type "submit"}]])))

(defn new-field
  [request]
  (ring-resp/response
   (hiccup/html
    [:p "New Field"]
    [:form {:action "add-field" :method "post"}
     [:input {:name "name"}]
     [:input {:type "submit"}]])))

(defn add-catalog
  [{params :form-params}]
  (peer/add-catalog (params "name"))
  (ring-resp/redirect-after-post "/"))

(defn add-field
  [{params :form-params}]
  (peer/add-field (params "name"))
  (ring-resp/redirect-after-post "/"))

(defn edit-catalog-fields
  [{params :query-params}]
  (let [catalog-id-str (:catalog-id params)
        catalog-id (Long/parseLong catalog-id-str)
        [catalog all-fields] (peer/get-catalog-and-all-fields catalog-id)
        [catalog-name catalog-fields] catalog
        catalog-field-ids (apply hash-set (map first catalog-fields))]
    (ring-resp/response
     (hiccup/html
      [:p (str "Fields of " catalog-name)]
      [:form {:action "submit-catalog-fields" :method "post"}
       (for [field all-fields]
         (let [[field-id field-name] field
               checked (contains? catalog-field-ids field-id)]
           [:li
            [:input {:type "checkbox" :name "field" :value field-id :checked checked} field-name]]))
       [:input {:type "hidden" :name "catalog-id" :value catalog-id}]
       [:input {:type "submit"}]]))))

(defn submit-catalog-fields
  [{params :form-params}]
  (let [field-field (params "field")
        field-id-strings (if (string? field-field) [field-field] field-field)
        field-ids (map #(Integer/parseInt %1) field-id-strings)
        catalog-id-str (params "catalog-id")
        catalog-id (Long/parseLong catalog-id-str)]
    (peer/relate-fields catalog-id field-ids)
    (ring-resp/redirect-after-post "/")))

(defroutes routes
  [[["/" {:get home-page}
     ^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/about" {:get about-page}]
     ["/new-catalog" {:get new-catalog}]
     ["/new-field" {:get new-field}]
     ["/add-catalog" {:post add-catalog}]
     ["/add-field" {:post add-field}]
     ["/edit-catalog-fields" {:get edit-catalog-fields}]
     ["/add-part" {:post add-part}]
     ["/submit-catalog-fields" {:post submit-catalog-fields}]
     ["/view-parts" {:get view-parts}]
     ["/new-part" {:get new-part}]
     ["/edit-part" {:get edit-part}]
     ["/update-part" {:post update-part}]
     ]]])

;; You can use this fn or a per-request fn via io.pedestal.service.http.route/url-for
(def url-for (route/url-for-routes routes))

;; Consumed by alm.server/create-server
(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ::bootstrap/port (Integer. (or (System/getenv "PORT") 8080))})

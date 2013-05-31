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
        (let [name (first catalog)]
          [:li [:p name
                [:a {:href (str "/edit-catalog-fields?catalog-name=" name)} "Edit Fields"]]]))]
     [:a {:href "/new-catalog"} "Create Catalog"]]
    [:p "Fields"
     [:ul
      (for [field (peer/get-fields)]
        [:li (first field)])]
     [:a {:href "/new-field"} "Create Field"]])))

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
  (let [catalog-name ( :catalog-name params)]
    (ring-resp/response
     (hiccup/html
      [:p (str "Fields of " catalog-name)]
      [:form {:action "submit-catalog-fields" :method "post"}
       (for [field (peer/get-fields)]
         (let [field-name (first field)]
           [:li
            [:input {:type "checkbox" :name "field" :value field-name} field-name]]))
       [:input {:type "hidden" :name "catalog-name" :value catalog-name}]
       [:input {:type "submit"}]]))))

(defn submit-catalog-fields
  [{params :form-params}]
  (let [field-field (params "field")
        field-names (if (string? field-field) [field-field] field-field)]
    (ring-resp/response
     (hiccup/html
      (for [field field-names]
        [:p field])))))

(defroutes routes
  [[["/" {:get home-page}
     ;; Set default interceptors for /about and any other paths under /
     ^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/about" {:get about-page}]
     ["/new-catalog" {:get new-catalog}]
     ["/new-field" {:get new-field}]
     ["/add-catalog" {:post add-catalog}]
     ["/add-field" {:post add-field}]
     ["/edit-catalog-fields" {:get edit-catalog-fields}]
     ["/submit-catalog-fields" {:post submit-catalog-fields}]]]])

;; You can use this fn or a per-request fn via io.pedestal.service.http.route/url-for
(def url-for (route/url-for-routes routes))

;; Consumed by alm.server/create-server
(def service {:env :prod
              ::bootstrap/routes routes
              ::bootstrap/resource-path "/public"
              ::bootstrap/type :jetty
              ::bootstrap/port (Integer. (or (System/getenv "PORT") 8080))})

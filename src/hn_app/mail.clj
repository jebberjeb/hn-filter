(ns hn-app.mail
  (:import (java.util Properties)
           (javax.mail.internet MimeMessage InternetAddress)
           (javax.mail Session Transport Authenticator
                       PasswordAuthentication Message$RecipientType)))

(defn send-gmail [{:keys [from to subject text user password]}]
  (let [auth (proxy [Authenticator] []
               (getPasswordAuthentication []
                 (PasswordAuthentication. user password)))
        props (doto (Properties.)
                (.putAll {"mail.smtp.user" user
                          "mail.smtp.port" "587"
                          "mail.smtp.host" "smtp.gmail.com"
                          "mail.smtp.starttls.enable" "true"
                          "mail.smtp.auth" "true"}))
        session (Session/getInstance props auth)
        msg (doto (MimeMessage. session)
              (.setContent text "text/html; charset=utf-8")
              (.setSubject subject)
              (.setFrom (InternetAddress. from)))]
    (doseq [addr to]
      (.addRecipient msg Message$RecipientType/TO (InternetAddress. addr)))
    (Transport/send msg)))

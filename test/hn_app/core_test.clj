(ns hn-app.core-test
  (:require [clojure.test :refer :all]
            [hn-app.core :refer :all]))

(deftest str-contains-any
  (is (str-contains-any? ["foo" "bar"] "foo bar baz"))
  (is (not (str-contains-any? ["qux"] "foo bar baz")))
  (is (not (str-contains-any? [] "foo bar baz")))
  (is (thrown? NullPointerException (str-contains-any? ["foo"] nil)))
  (is (not (str-contains-any? nil "foo bar baz"))))

(deftest item-to-a
  (is (= "<a href=\"foo\">bar</a> - <a href=\"baz\">1 comments</a>"
         (item->a {:item-name "bar" :item-link "foo"
                   :comment-count 1 :comment-link "baz"}))))


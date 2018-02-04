(ns repl.import
  (:require [clojurians-log.data :refer :all]
            [clojure.set :as set]
            [clojure.string :as str]
            [datomic.api :as d]))


;; This is an attempt at figuring out the structure and semantics of the various
;; events that we have stored. For the basics, see
;; https://api.slack.com/events/message


;; channels
(into {} (comp
          (filter #(contains? #{"clojure" "clojurescript"} (:name %)))
          (map (juxt :id identity)))
      (vals (channels)))

(def ++chans++
  {"C03S1L9DN" {:id "C03S1L9DN",
                :name "clojurescript",
                :created 1425233874,
                :creator "U03RZGPFT"},
   "C03S1KBA2" {:id "C03S1KBA2",
                :name "clojure",
                :created 1425233492,
                :creator "U03RZGPFT"}})

(def ++chan-id++ "C03S1L9DN" #_"C03S1KBA2")

;; all messages from November 2017
(def ++msgs++ (mapcat #(event-seq (format "logs/2017-11-%02d.txt" %)) (range 30)))

;; message keys
(def ++msg-keys++ (sort (into #{} (comp (map keys) cat) ++msgs++)))


;; | slack API         | datomic       |
;; |-------------------+---------------|
;; | :attachments      |               |
;; | :bot_id           |               |
;; | :bot_link         |               |
;; | :channel          |               |
;; | :comment          |               |
;; | :deleted_ts       |               |
;; | :display_as_bot   |               |
;; | :event_ts         |               |
;; | :file             |               |
;; | :hidden           |               |
;; | :inviter          |               |
;; | :is_auto_split    |               |
;; | :is_intro         |               |
;; | :item_type        |               |
;; | :message          |               |
;; | :previous_message |               |
;; | :purpose          |               |
;; | :reply_broadcast  |               |
;; | :source_team      |               |
;; | :subtype          |               |
;; | :team             |               |
;; | :text             | :message/text |
;; | :thread_ts        |               |
;; | :topic            |               |
;; | :ts               | :slack/ts     |
;; | :type             | :event/type   |
;; | :upload           |               |
;; | :user             | :message/user |
;; | :user_profile     |               |
;; | :user_team        |               |
;; | :username         |               |


;; message types
(into #{} (map :type) ++msgs++)
#{"message"}


(def ++subtypes++
  (into #{} (map :subtype) ++msgs++))
#{nil "message_deleted" "bot_message" "message_changed" "channel_archive" "channel_join" "file_share" "channel_purpose" "bot_add" "channel_topic" "message_replied" "file_comment" "file_mention" "me_message" "pinned_item" "bot_remove" "channel_leave"}

(->> ++msgs++
     (into #{} (comp (keep :message)
                     (map keys)
                     cat))
     sort
     (remove #(contains? (set ++msg-keys++) %)))


;; All :message keys
(:attachments
 :bot_id
 :comment
 :display_as_bot
 :edited
 :file
 :is_intro
 :parent_user_id
 :replies
 :reply_count
 :root
 :subtype
 :text
 :thread_ts
 :ts
 :type
 :unread_count
 :upload
 :user
 :username)

;; :message keys that aren't top level keys
(:edited :parent_user_id :replies :reply_count :root :unread_count)

(defn msgs-by-subtype [st]
  (filter #(= st (:subtype %)) ++msgs++))

(def ++keys-by-subtype++
  (into {} (comp
            (map (juxt identity #(->> %
                                      msgs-by-subtype
                                      (mapcat keys)
                                      (into #{}))))) ++subtypes++))

;; shared keys
(def ++shared-keys++
  (reduce set/intersection (vals ++keys-by-subtype++)))
#{:event_ts :channel :type :ts}

(into []
      (sort-by first
               (map (fn [subtypes]
                      [(into [] (sort (map first subtypes)))
                       (into [] (sort (last (first subtypes))))])
                    (partition-by (comp vec sort last)
                                  (sort-by (comp vec sort last)
                                           (map (juxt first (comp #(remove ++shared-keys++ %) last))
                                                ++keys-by-subtype++))))))


;; Keys that messages can have, by subtype, excluding the keys that all messages carry:
;; ts, event_ts, channel, type, subtype (shared by all but one)
[[[nil] [:attachments
         :bot_id
         :reply_broadcast
         :source_team
         :team
         :text
         :thread_ts
         :user
         :user_profile
         :user_team]]
 [["bot_message"] [:attachments :bot_id :is_auto_split :team :text :username]]
 [["channel_join"] [:inviter :team :text :user :user_profile]]
 [["channel_purpose"] [:purpose :team :text :user :user_profile]]
 [["channel_topic"] [:team :text :topic :user :user_profile]]
 [["file_comment"] [:comment
                    :file
                    :is_intro
                    :source_team
                    :team
                    :text
                    :user_profile
                    :user_team]]
 [["file_mention"] [:file :source_team :team :text :user
                    :user_profile :user_team]]
 [["file_share"] [:bot_id
                  :display_as_bot
                  :file
                  :source_team
                  :team
                  :text
                  :upload
                  :user
                  :user_profile
                  :user_team
                  :username]]
 [["me_message"] [:team :text :user]]
 [["message_changed"] [:hidden :message :previous_message]]
 [["message_deleted"] [:deleted_ts :hidden :previous_message]]
 [["message_replied"] [:hidden :message]]
 [["pinned_item"] [:attachments :item_type :team :text :user]]
 [["bot_add" "bot_remove"] [:bot_id :bot_link :team :text :user]]
 [["channel_archive" "channel_leave"] [:team :text :user :user_profile]]]

(defn sample-message
  ([subtype]
   (rand-nth (filter #(= (:subtype %) subtype) ++msgs++)))
  ([subtype has-key]
   (rand-nth (filter #(and (= (:subtype %) subtype) (contains? % has-key)) ++msgs++))))

(sample-message nil)
;; => {:source_team "T03RZGPFR",
;;     :text
;;     "`hand` is a vector with at most like ten items, so i’m not worried about performance here, just trying to figure out the nicest way to express this function",
;;     :ts "1512262977.000052",
;;     :user "U0M88SVQQ",
;;     :team "T03RZGPFR",
;;     :type "message",
;;     :channel "C053AK3F9"}

{:source_team "T03RZGPFR",
 :text "<https://adventofcode.com/2017/leaderboard/private/view/217019>",
 :ts "1512403280.000684",
 :user "U04V15CAJ",
 :team "T03RZGPFR",
 :type "message",
 :channel "C0GLTDB2T"}

(sample-message nil :thread_ts)
{:thread_ts "1512146059.000668",
 :source_team "T03RZGPFR",
 :text "<http://docs.datomic.com/transactions.html#explicit-db-txinstant>\n<http://docs.datomic.com/best-practices.html#set-txinstant-on-imports>\nan example doing it here:\n<https://github.com/Datomic/day-of-datomic/blob/master/resources/streets.edn> and <https://github.com/Datomic/day-of-datomic/blob/master/tutorial/log.clj>",
 :ts "1512155233.000395",
 :user "U05120CBV",
 :team "T03RZGPFR",
 :type "message",
 :channel "C03RZMDSH"}

(sample-message nil :attachments)



(sample-message "message_changed")
{:event_ts "1512384964.000297",
 :ts "1512384964.000297",
 :subtype "message_changed",
 :message {:text
           "in the error `Uncaught Error: [object Object] is not ISeqable at Object.cljs$core$seq` [as seq] - the for is trying to open a seq over the subscription's reaction object, but it can't",
           :type "message",
           :user "U051H1KL1",
           :ts "1512384942.000382",
           :edited {:user "U051H1KL1"
                    :ts "1512384964.000000"}},
 :type "message",
 :hidden true,
 :channel "C073DKH9P",
 :previous_message {:text
                    "in the error `Uncaught Error: [object Object] is not ISeqable at Object.cljs$core$seq` [as seq] - the for loop is trying to open a seq over the subscription's reaction object",
                    :type "message",
                    :user "U051H1KL1",
                    :ts "1512384942.000382",
                    :edited {:user "U051H1KL1"
                             :ts "1512384953.000000"}}}
(count
 (set (map (juxt :channel :ts) ++msgs++)))
27350i

{:message/key "C073DKH9P--1512384953.000000"
 :message/ts "1512384953.000000"
 :message/text "hi I'm on slack!"
 :message/user {:ref ...}
 :message/channel {:ref ...}}

(-> (users)
    vals
    rand-nth
    :profile
    keys
    (->> (map (fn [k]
                [(-> k
                     name
                     (str/replace "_" "-")
                     (keyword))
                 (symbol (name k))]))
         (into {})))


(require '[clojurians-log.db.import :as i]
         '[datomic.api :as d])

(ns-unalias *ns* 'i)

(defn conn []
  (-> reloaded.repl/system
      :datomic
      :conn))

(d/transact
 (conn)
 [
  (i/user->tx (-> (users) vals rand-nth))])

(take 5 (repeatedly #(:id (rand-nth (vals (users))))))
(take 5 (repeatedly #(:id (rand-nth (vals (channels))))))
("C7V2MGS84" "C0675SLET" "C0GLTDB2T" "C7WGFBD47" "C3NF8UC9J")
("U0DA8ELJK" "U0F2ZGEKV" "U05R6XUARE" "U055BC9QC" "U067C1A9E")

(into {} (d/entity (d/db (conn)) [:user/slack-id "U0DA8ELJK" ]))
(into {} (:channel/creator (d/entity (d/db (conn)) [:channel/slack-id "C7WGFBD47" ])))



(d/touch (d/entity (d/db (conn)) 94))


(->> (channels)
     vals
     (map keys)
     (into #{})
     sort
     )

(rand-nth (vals (channels)))

((:id :name :created :creator))

;; Load schema
(d/transact (conn) clojurians-log.db.schema/full-schema)

;; Load users
(d/transact (conn) (mapv i/user->tx (vals (users))))

;; Load channels
(d/transact (conn) (mapv i/channel->tx (vals (channels))))

;; Load messages
(d/transact (conn) (keep i/event->tx ++msgs++))

(d/q '[:find ?t
       :where
       [?m :message/text ?t]
       [?m :message/channel ?c]
       [?c :channel/name "chestnut"]]
     (d/db (conn)))


(d/q '[:find ?d ?t
       :where
       [?m :message/day ?d]
       [?m :message/text ?t]
       [?m :message/channel ?c]
       [?c :channel/name "chestnut"]]
     (d/db (conn)))

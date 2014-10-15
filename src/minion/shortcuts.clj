(ns minion.shortcuts)

(defn shortcuts!
  "Create shortcuts for namespace switching. Takes a map of `function -> target-namespace`
   associations and interns the given function into all other target namespaces, as well
   as the `user` one."
  ([m] (shortcuts! m false))
  ([m force?]
   {:pre [(or (not m) (map? m))
          (every? symbol? (keys m))
          (every? symbol? (vals m))]}
   (->> (for [[f target-ns] m
              src-ns (distinct (cons 'user (vals m)))
              :when (not= target-ns src-ns)]
          (try
            (let [nspace (create-ns src-ns)
                  goto-ns! #(in-ns target-ns)
                  r (ns-resolve nspace f)]
              (if (and (not force?) r)
                (printf "WARN: %s would be overwritten. not creating shortcut.%n" r)
                (intern src-ns f goto-ns!)))
            (catch Throwable ex
              (printf "WARN: could not create shortcut '%s' [%s -> %s]: %s%n"
                      f src-ns target-ns (.getMessage ex)))))
        (filter identity)
        (vec))))

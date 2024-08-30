Scour

A very small Clojure library intended for crawling and downloading webpages.

## Usage

Require the code namespace:
```clojure
(ns example
  (:require [scour.core :as scour]))
```

Provide a URL and scan all the resources on the domain

```clojure
(def results (scour/scan "https://example.com/"))
(count results) 
=> 1
```

Provide optional filters to include resources that are outside of the domain.
```clojure
(defn example-filter [s]
  (str/ends-with? s "/example"))

(def results (scour/scan "https://example.com/" {:inc-filters [example-filter]}))

(map first results)
=> ("https://example.com" "https://www.iana.org/domains/example")

```

## License

Copyright © 2024 Daniel Heiniger

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
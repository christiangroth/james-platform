* Apps können eine Konfiguration definieren: eine Einzel-Entität mit genau einem Datensatz pro Installation, der beim Installieren aus den Defaults angelegt wird.
* Nutzer pflegen die Konfiguration einer App über den neuen Button „Einstellungen“; sie erscheint nicht mehr als normale Datenliste und kann nicht gelöscht werden.
* Reports lesen Konfigurationswerte als `config.<Feld>`: in Filterwerten, als Schwellwerte und als konstanter Einzelwert (z. B. Jahresziel).
* Berechnete Properties können über `config` auf die Konfigurationswerte zugreifen.
* Die Entwickler-API kann eine Entität per `PUT …/entities/{entityId}/configuration` als Konfiguration markieren.

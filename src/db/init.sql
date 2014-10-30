CREATE TABLE IF NOT EXISTS schemas (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  duration INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS key_behaviors (
  schema_id INTEGER NOT NULL,
  key CHAR(1) NOT NULL,
  behavior TEXT NOT NULL,
  is_continuous INTEGER NOT NULL,

  FOREIGN KEY (schema_id) REFERENCES schemas(id),
  PRIMARY KEY(schema_id, key)
);
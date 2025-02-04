CREATE USER 'debezium'@'%' IDENTIFIED WITH mysql_native_password BY 'dbz_pass';
CREATE USER 'replicator'@'%' IDENTIFIED BY 'repl_pass';

GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT  ON *.* TO 'debezium';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'replicator';

CREATE USER 'debezium' IDENTIFIED WITH mysql_native_password BY 'dbz_pass';
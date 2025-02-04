# In production you would almost certainly limit the replication user must be on the follower (slave) machine,
# to prevent other clients accessing the log from other machines. For example, 'replicator'@'follower.acme.com'.
#
# However, this grant is equivalent to specifying *any* hosts, which makes this easier since the docker host
# is not easily known to the Docker container. But don't do this in production.
#
CREATE USER 'replicator'@'%' IDENTIFIED BY 'replpass';
CREATE USER 'confluent'@'%' IDENTIFIED BY 'connect';
GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'replicator';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT  ON *.* TO 'confluent';

# Create the database that we'll use to populate data and watch the effect in the binlog
CREATE DATABASE accounts;
GRANT ALL PRIVILEGES ON accounts.* TO 'mysqluser'@'%';

# Switch to this database
USE accounts;

# Create some customers ...
CREATE TABLE customers (
  id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
  first_name VARCHAR(255) NOT NULL,
  last_name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE KEY
) AUTO_INCREMENT=1001;

INSERT INTO customers
VALUES (default,"Jay","Kreps","jk@cflt.com"),
       (default,"Jun","Rao","jr@cflt.com"),
       (default,"Neha","Narkhede","nn@oscilar.com"),
       (default,"Dennis","Federico","df@noanswer.org"),
       (default,"Tony","Hawks","th@skate.org");


# Create some fake addresses
CREATE TABLE addresses (
  id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer_id INTEGER NOT NULL,
  street VARCHAR(255) NOT NULL,
  city VARCHAR(255) NOT NULL,
  state VARCHAR(255) NOT NULL,
  FOREIGN KEY address_customer (customer_id) REFERENCES customers(id)
) AUTO_INCREMENT = 10;

INSERT INTO addresses
VALUES (default,1001,'3183 Moore Avenue','Euless','Texas'),
       (default,1001,'2389 Hidden Valley Road','Harrisburg','Pennsylvania'),
       (default,1002,'281 Riverside Drive','Augusta','Georgia'),
       (default,1003,'3787 Brownton Road','Columbus','Mississippi'),
       (default,1003,'2458 Lost Creek Road','Bethlehem','Pennsylvania'),
       (default,1004,'1289 University Hill Road','Canehill','Arkansas'),
       (default,1003,'4800 Simpson Square','Hillsdale','Oklahoma'),
       (default,1005,'1234 Skate Park Road','Los Angeles','California');

# Account and their balances
CREATE TABLE account_balances (
  id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
  customer_id INTEGER NOT NULL,  
  balance DECIMAL(15, 2),
  FOREIGN KEY balance_customer (customer_id) REFERENCES customers(id)
);
ALTER TABLE account_balances AUTO_INCREMENT = 901;

INSERT INTO account_balances
VALUES (default,1001,3567890.25),
       (default,1002,1234567.75),
       (default,1003,2345678.50),
       (default,1004,9999.99),
       (default,1005,100000);

# Simple Credit/Debits to an accoun (Not a real transaction table)
CREATE TABLE account_movements (
  movement_id INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY,
  account_id INTEGER NOT NULL,
  customer_id INTEGER NOT NULL,
  movement_date DATE NOT NULL,
  amount DECIMAL(15, 2) NOT NULL,  
  FOREIGN KEY (account_id) REFERENCES account_balances(id),
  FOREIGN KEY (customer_id) REFERENCES customers(id)
) AUTO_INCREMENT = 67890;

INSERT INTO account_movements VALUES (default,904,1004,'2025-01-01',1000);
INSERT INTO account_movements VALUES (default,904,1004,'2025-01-02',-400);
INSERT INTO account_movements VALUES (default,904,1004,'2025-01-02',-700);
INSERT INTO account_movements VALUES (default,904,1004,'2025-01-03',1000);

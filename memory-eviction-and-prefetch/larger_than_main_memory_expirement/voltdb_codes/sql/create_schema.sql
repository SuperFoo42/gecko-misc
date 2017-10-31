CREATE TABLE region (
	r_regionkey	INT	NOT NULL,
r_name	VARCHAR(25)	NOT NULL,
r_comment	VARCHAR(152)
);

CREATE TABLE nation (
n_nationkey	INT	NOT NULL,
n_name	VARCHAR(25)	NOT NULL,
n_regionkey	INT	NOT NULL,
n_comment	VARCHAR(152)
);

CREATE TABLE supplier (
s_suppkey	INT	NOT NULL,
s_name	VARCHAR(25)	NOT NULL,
s_address	VARCHAR(40)	NOT NULL,
s_nationkey	INT	NOT NULL,
s_phone	VARCHAR(15)	NOT NULL,
s_acctbal	FLOAT	NOT NULL,
s_comment	VARCHAR(101)	NOT NULL
);

CREATE TABLE customer (
c_custkey	INT	NOT NULL,
c_name	VARCHAR(25)	NOT NULL,
c_address	VARCHAR(40)	NOT NULL,
c_nationkey	INT	NOT NULL,
c_phone	VARCHAR(15)	NOT NULL,
c_acctbal	FLOAT	NOT NULL,
c_mktsegment	VARCHAR(10)	NOT NULL,
c_comment	VARCHAR(117)	NOT NULL
);

CREATE TABLE part (
p_partkey	INT	NOT NULL,
p_name	VARCHAR(55)	NOT NULL,
p_mfgr	VARCHAR(25)	NOT NULL,
p_brand	VARCHAR(10)	NOT NULL,
p_type	VARCHAR(25)	NOT NULL,
p_size	INT	NOT NULL,
p_container	VARCHAR(10)	NOT NULL,
p_retailprice	FLOAT	NOT NULL,
p_comment	VARCHAR(23)	NOT NULL
);

CREATE TABLE partsupp (
ps_partkey	INT	NOT NULL,
ps_suppkey	INT	NOT NULL,
ps_availqty	INT	NOT NULL,
ps_supplycost	FLOAT	NOT NULL,
ps_comment	VARCHAR(199)	NOT NULL
);

CREATE TABLE orders (
o_orderkey	INT	NOT NULL, 
o_custkey	INT	NOT NULL, 
o_orderstatus	VARCHAR(1)	NOT NULL, 
o_totalprice	FLOAT	NOT NULL, 
o_orderdate	TIMESTAMP	NOT NULL, 
o_orderpriority	VARCHAR(15)	NOT NULL, 
o_clerk	VARCHAR(15)	NOT NULL, 
o_shippriority	INT	NOT NULL,
o_comment	VARCHAR(79)	NOT NULL 
 );

CREATE TABLE lineitem (
l_orderkey	INT	NOT NULL, 
l_partkey	INT	NOT NULL, 
l_suppkey	INT	NOT NULL, 
l_linenumber	INT	NOT NULL, 
l_quantity	FLOAT	NOT NULL, 
l_extendedprice	FLOAT	NOT NULL, 
l_discount	FLOAT	NOT NULL, 
l_tax	FLOAT	NOT NULL, 
l_returnflag	VARCHAR(1)	NOT NULL, 
l_linestatus	VARCHAR(1)	NOT NULL, 
l_shipdate	TIMESTAMP	NOT NULL, 
l_commitdate	TIMESTAMP	NOT NULL, 
l_receiptdate	TIMESTAMP	NOT NULL, 
l_shipinstruct	VARCHAR(25)	NOT NULL, 
l_shipmode	VARCHAR(10)	NOT NULL, 
l_comment	VARCHAR(44)	NOT NULL 
);
PARTITION TABLE customer ON COLUMN c_custkey;
PARTITION TABLE lineitem ON COLUMN l_linenumber;
PARTITION TABLE orders   ON COLUMN o_orderkey;
PARTITION TABLE partsupp ON COLUMN ps_suppkey;
PARTITION TABLE part     ON COLUMN p_partkey;
PARTITION TABLE supplier ON COLUMN s_suppkey;
PARTITION TABLE nation   ON COLUMN n_nationkey;
PARTITION TABLE region   ON COLUMN r_regionkey;

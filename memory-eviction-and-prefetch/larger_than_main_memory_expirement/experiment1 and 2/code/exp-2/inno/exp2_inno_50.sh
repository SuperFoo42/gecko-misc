#!/bin/bash
sudo rm /etc/mysql/mysql.conf.d/mysqld.cnf
sudo cp ./50/mysqld.cnf  /etc/mysql/mysql.conf.d/

sudo /etc/init.d/mysql restart

##############  first creating the database #########################################

mysql -u root -pmysql <<MY_QUERY

drop database IF EXISTS ycsb ;

create database ycsb;


MY_QUERY


echo "100% read"
./oltpbenchmark -b ycsb -c config/exp2_configs/1.0.xml --create=true --load=true --execute=true -s 60 -o inno_50_1.0.txt | tee inno_50_ycsb_1.0.txt


echo "75% read"

sudo /etc/init.d/mysql restart

mysql -u root -pmysql <<MY_QUERY

drop database IF EXISTS ycsb ;

create database ycsb;

MY_QUERY

./oltpbenchmark -b ycsb -c config/exp2_configs/0.75.xml --create=true --load=true --execute=true  --execute=true -s 60 -o inno_50_0.75.txt | tee inno_50_ycsb_0.75.txt

sudo /etc/init.d/mysql restart

mysql -u root -pmysql <<MY_QUERY

drop database IF EXISTS ycsb ;

create database ycsb;

MY_QUERY


echo "50% read"
./oltpbenchmark -b ycsb -c config/exp2_configs/0.5.xml --create=true --load=true --execute=true -s 60 -o inno_50_0.5.txt | tee inno_50_ycsb_0.5.txt


sudo /etc/init.d/mysql restart

mysql -u root -pmysql <<MY_QUERY

drop database IF EXISTS ycsb ;

create database ycsb;

MY_QUERY



echo "25% read"
./oltpbenchmark -b ycsb -c config/exp2_configs/0.25.xml --create=true --load=true --execute=true -s 60 -o inno_50_0.25.txt | tee inno_50_ycsb_0.25.txt


sudo /etc/init.d/mysql restart

mysql -u root -pmysql <<MY_QUERY

drop database IF EXISTS ycsb ;

create database ycsb;

MY_QUERY



echo "0% read"
./oltpbenchmark -b ycsb -c config/exp2_configs/0.0.xml --create=true --load=true --execute=true -s 60 -o inno_50_0.0.txt | tee inno_50_ycsb_0.0.txt


sudo /etc/init.d/mysql restart

mysql -u root -pmysql <<MY_QUERY

drop database IF EXISTS ycsb ;


MY_QUERY


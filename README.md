shadowsocks-vertx
================

[![Build Status](https://travis-ci.org/Bestoa/shadowsocks-vertx.svg?branch=master)](https://travis-ci.org/Bestoa/shadowsocks-vertx)
[![codecov](https://codecov.io/gh/Bestoa/shadowsocks-vertx/branch/master/graph/badge.svg)](https://codecov.io/gh/Bestoa/shadowsocks-vertx)
[![License](http://img.shields.io/:license-apache-blue.svg?style=flat-square)](http://www.apache.org/licenses/LICENSE-2.0.html)

#Intro

shadowsocks-vertx is a lightweight tunnel proxy. It is based on vert.x.

Java 8 is needed.

It is **unstable**! If you encounter any problems, please open an issue.

About shadowsocks, please refer to https://shadowsocks.org/

About vert.x, please refer to http://vertx.io/

#Features

**Not compatible with original Shadowsocks**!

Not support ipv6.

Support JSON config file. Please refer to https://github.com/shadowsocks/shadowsocks/wiki/Configuration-via-Config-File.
Note:

    1. Not support: local_address, client will bind 0.0.0.0 not 127.0.0.1
    2. Not support: fast_open. Java doesn't have such feature.
    3. Not support: workers. Vertx will set work thread number automatically.
    4. Additional: server_mode, set the running mode, true for the server, false for the client.
    5. Additional: iv_len, set the iv length, the value must be greater than 16 ! 
    6. Not support: method, just only rc4-sha512 ! 

You could refer to demo config etc/demo.json.

How to run:
===========

### (1) generate fat jar
```
$ gradle clean build fatJar
```


Then you will get **shadowsocks-fat-ver.jar** in build/libs.

### (2) run
```
$ java -jar shadowsocks-fat-ver.jar configFile
```


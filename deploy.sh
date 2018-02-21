echo "------ kill java"
killall java
sleep 2s
echo "------ kill complete"

echo "------ pull code"
cd /home/javaSource/shadowsocks-vertx/
git pull origin ss4me

echo "------ build"
gradle clean build fatJar

echo "------ copy"
cp /home/javaSource/shadowsocks-vertx/build/libs/shadowsocks-fat-0.9.0.jar /home/vertx/

echo "------ start"
cd /home/vertx/
java -jar shadowsocks-fat-0.9.0.jar config.json > logs/consoleSS.log 2>&1  &

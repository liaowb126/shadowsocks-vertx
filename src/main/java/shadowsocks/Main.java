package shadowsocks;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import shadowsocks.crypto.CryptoFactory;

import shadowsocks.util.GlobalConfig;

public class Main{



    public static final String VERSION = "0.9.0";

    public static void main(String argv[])
    {

        System.setProperty("vertx.logger-delegate-factory-class-name",
                "io.vertx.core.logging.SLF4JLogDelegateFactory");

        final Logger log = LoggerFactory.getLogger(Main.class);

        // IPV4 优先
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");

        log.info("Shadowsocks " + VERSION);

        if (argv.length != 1) {
            throw new RuntimeException("argvError ! ");
        }

        try {
            // 加载配置文件
            String configFile = argv[0];
            GlobalConfig.get().setConfigFile(configFile);
            GlobalConfig.getConfigFromFile();
        } catch (Exception e) {
            log.fatal("get config from file error",e);
            return;
        }

        // iv.len 必须大于 16
        if (GlobalConfig.get().getIvLen() <= 16) {
            log.error("iv len must be greater than 16 !");
            return ;
        }


        //make sure this method could work.
        try{
            CryptoFactory.create(GlobalConfig.get().getMethod(), GlobalConfig.get().getPassword());
        }catch(Exception e){
            log.fatal("Error crypto method", e);
            return;
        }
        GlobalConfig.get().printConfig();
        new ShadowsocksVertx(GlobalConfig.get().isServerMode()).start();
    }
}
